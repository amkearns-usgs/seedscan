/*
 * Copyright 2012, United States Geological Survey or
 * third-party contributors as indicated by the @author tags.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/  >.
 *
 */

package asl.plotmaker;

import java.awt.Color;
import java.awt.Paint;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.Range;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asl.metadata.Channel;
import asl.metadata.Station;

/**
 * @author Mike Hagerty hagertmb@bc.edu
 */
public class PlotMaker {
	private static final Logger logger = LoggerFactory
			.getLogger(asl.plotmaker.PlotMaker.class);
	private Station station;
	private Channel channel;
	private LocalDate date;
	private final String outputDir = "outputs";

	// constructor(s)
	public PlotMaker(Station station, Channel channel, LocalDateTime timestamp) {
		this.station = station;
		this.channel = channel;
		this.date = timestamp.toLocalDate();
	}
	
	public PlotMaker(Station station, Channel channel, LocalDate date) {
		this.station = station;
		this.channel = channel;
		this.date = date;
	}

	public void plotSpecAmp2(double freq[], double[] amp1, double[] phase1,
			double[] amp2, double[] phase2, String plotTitle, String pngName) {

		/**
		 * final String plotTitle = String.format("%04d%03d.%s.%s %s",
		 * date.get(Calendar.YEAR), date.get(Calendar.DAY_OF_YEAR) ,station,
		 * channel, plotString); final String pngName =
		 * String.format("%s/%04d%03d.%s.%s.%s.png", outputDir,
		 * date.get(Calendar.YEAR), date.get(Calendar.DAY_OF_YEAR) ,station,
		 * channel, plotString);
		 **/
		File outputFile = new File(pngName);

		// Check that we will be able to output the file without problems and if
		// not --> return
		if (!checkFileOut(outputFile)) {
			logger.warn("== plotSpecAmp: request to output plot=[{}] date=[{}] but we are unable to create it "
							+ " --> skip plot", pngName, date.format(DateTimeFormatter.ISO_ORDINAL_DATE));
			return;
		}
		// Plot x-axis (frequency) range
		final double XMIN = .00009;
		final double XMAX = freq[freq.length - 1];

		System.out.format("== plotSpecAmp2: nfreq=%d npts=%d pngName=%s\n",
				freq.length, amp2.length, pngName);

		final XYSeries series1 = new XYSeries("Amp_PZ");
		final XYSeries series1b = new XYSeries("Amp_Cal");

		final XYSeries series2 = new XYSeries("Phase_PZ");
		final XYSeries series2b = new XYSeries("Phase_Cal");

		double maxdB = 0.;
		for (int k = 0; k < freq.length; k++) {
			double dB = amp1[k];
			// double dB = 20. * Math.log10( amp1[k] );
			// series1.add( freq[k], dB );
			// series1.add( freq[k], 20. * Math.log10( amp1[k] ) );
			// series1b.add(freq[k], 20. * Math.log10( amp2[k] ));
			series1.add(freq[k], amp1[k]);
			series1b.add(freq[k], amp2[k]);
			series2.add(freq[k], phase1[k]);
			series2b.add(freq[k], phase2[k]);
			if (dB > maxdB) {
				maxdB = dB;
			}
		}

		// final XYItemRenderer renderer = new StandardXYItemRenderer();
		final XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
		Rectangle rectangle = new Rectangle(3, 3);
		renderer.setSeriesShape(0, rectangle);
		// renderer.setSeriesShapesVisible(0, true);
		renderer.setSeriesShapesVisible(0, false);
		renderer.setSeriesLinesVisible(0, true);

		renderer.setSeriesShape(1, rectangle);
		renderer.setSeriesShapesVisible(1, true);
		renderer.setSeriesLinesVisible(1, false);

		Paint[] paints = new Paint[] { Color.red, Color.blue };
		renderer.setSeriesPaint(0, paints[0]);
		// renderer.setSeriesPaint(1, paints[1]);

		final XYLineAndShapeRenderer renderer2 = new XYLineAndShapeRenderer();
		renderer2.setSeriesPaint(0, paints[1]);
		renderer2.setSeriesShapesVisible(0, false);
		renderer2.setSeriesLinesVisible(0, true);

		// Stroke is part of Java Swing ...
		// renderer2.setBaseStroke( new Stroke( ... ) );

		double ymax;
		if (maxdB < 10) {
			ymax = 10.;
		} else {
			ymax = maxdB + 2;
		}

		final NumberAxis verticalAxis = new NumberAxis("Spec Amp (dB)");
		verticalAxis.setRange(new Range(-40, ymax));
		verticalAxis.setTickUnit(new NumberTickUnit(5));

		// final LogarithmicAxis verticalAxis = new
		// LogarithmicAxis("Amplitude Response");
		// verticalAxis.setRange( new Range(0.01 , 10) );

		final LogarithmicAxis horizontalAxis = new LogarithmicAxis(
				"Frequency (Hz)");
		// horizontalAxis.setRange( new Range(0.0001 , 100.5) );
		// horizontalAxis.setRange( new Range(0.00009 , 110) );
		horizontalAxis.setRange(new Range(XMIN, XMAX));

		final XYSeriesCollection seriesCollection = new XYSeriesCollection();
		seriesCollection.addSeries(series1);
		seriesCollection.addSeries(series1b);

		final XYPlot xyplot = new XYPlot(seriesCollection, null,
				verticalAxis, renderer);
		// final XYPlot xyplot = new XYPlot((XYDataset)seriesCollection,
		// horizontalAxis, verticalAxis, renderer);

		xyplot.setDomainGridlinesVisible(true);
		xyplot.setRangeGridlinesVisible(true);
		xyplot.setRangeGridlinePaint(Color.black);
		xyplot.setDomainGridlinePaint(Color.black);

		final NumberAxis phaseAxis = new NumberAxis("Phase (Deg)");
		phaseAxis.setRange(new Range(-180, 180));
		phaseAxis.setTickUnit(new NumberTickUnit(30));
		final XYSeriesCollection seriesCollection2 = new XYSeriesCollection();
		seriesCollection2.addSeries(series2);
		seriesCollection2.addSeries(series2b);
		final XYPlot xyplot2 = new XYPlot(seriesCollection2, null,
				phaseAxis, renderer2);

		// CombinedXYPlot combinedPlot = new CombinedXYPlot( horizontalAxis,
		// CombinedXYPlot.VERTICAL );
		CombinedDomainXYPlot combinedPlot = new CombinedDomainXYPlot(
				horizontalAxis);
		combinedPlot.add(xyplot, 1);
		combinedPlot.add(xyplot2, 1);
		combinedPlot.setGap(15.);

		// final JFreeChart chart = new JFreeChart(xyplot);
		final JFreeChart chart = new JFreeChart(combinedPlot);
		chart.setTitle(new TextTitle(plotTitle));

		try {
			ChartUtilities.saveChartAsPNG(outputFile, chart, 1000, 800);
		} catch (IOException e) {
			// System.err.println("Problem occurred creating chart.");
			logger.error("IOException:", e);
		}
	} // end plotResp

	public void plotSpecAmp(double freq[], double[] amp, double[] phase,
			String plotString) {

		// plotTitle = "2012074.IU_ANMO.00-BHZ " + plotString
		final String plotTitle = String.format("%04d%03d.%s.%s %s",
				date.getYear(), date.getDayOfYear(),
				station, channel, plotString);
		// plot filename = "2012074.IU_ANMO.00-BHZ" + plotString + ".png"
		final String pngName = String.format("%s/%04d%03d.%s.%s.%s.png",
				outputDir, date.getYear(),
				date.getDayOfYear(), station, channel, plotString);

		File outputFile = new File(pngName);

		// Check that we will be able to output the file without problems and if
		// not --> return
		if (!checkFileOut(outputFile)) {
			// System.out.format("== plotSpecAmp: request to output plot=[%s] but we are unable to create it "
			// + " --> skip plot\n", pngName );
			logger.warn(
					"== plotSpecAmp: request to output plot=[{}] date=[{}] but we are unable to create it "
							+ " --> skip plot", pngName, date.format(DateTimeFormatter.ISO_ORDINAL_DATE));
			return;
		}

		final XYSeries series1 = new XYSeries("Amplitude");
		final XYSeries series2 = new XYSeries("Phase");

		double maxdB = 0.;
		for (int k = 0; k < freq.length; k++) {
			double dB = 20. * Math.log10(amp[k]);
			series1.add(freq[k], dB);
			series2.add(freq[k], phase[k]);
			if (dB > maxdB) {
				maxdB = dB;
			}
		}

		// final XYItemRenderer renderer = new StandardXYItemRenderer();
		final XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
		Rectangle rectangle = new Rectangle(3, 3);
		renderer.setSeriesShape(0, rectangle);
		// renderer.setSeriesShapesVisible(0, true);
		renderer.setSeriesShapesVisible(0, false);
		renderer.setSeriesLinesVisible(0, true);

		renderer.setSeriesShape(1, rectangle);
		renderer.setSeriesShapesVisible(1, true);
		renderer.setSeriesLinesVisible(1, false);

		Paint[] paints = new Paint[] { Color.red, Color.blue };
		renderer.setSeriesPaint(0, paints[0]);
		// renderer.setSeriesPaint(1, paints[1]);

		final XYLineAndShapeRenderer renderer2 = new XYLineAndShapeRenderer();
		renderer2.setSeriesPaint(0, paints[1]);
		renderer2.setSeriesShapesVisible(0, false);
		renderer2.setSeriesLinesVisible(0, true);

		// Stroke is part of Java Swing ...
		// renderer2.setBaseStroke( new Stroke( ... ) );

		double ymax;
		if (maxdB < 10) {
			ymax = 10.;
		} else {
			ymax = maxdB + 2;
		}

		final NumberAxis verticalAxis = new NumberAxis("Spec Amp (dB)");
		verticalAxis.setRange(new Range(-40, ymax));
		verticalAxis.setTickUnit(new NumberTickUnit(5));

		// final LogarithmicAxis verticalAxis = new
		// LogarithmicAxis("Amplitude Response");
		// verticalAxis.setRange( new Range(0.01 , 10) );

		final LogarithmicAxis horizontalAxis = new LogarithmicAxis(
				"Frequency (Hz)");
		// horizontalAxis.setRange( new Range(0.0001 , 100.5) );
		horizontalAxis.setRange(new Range(0.00009, 110));

		final XYSeriesCollection seriesCollection = new XYSeriesCollection();
		seriesCollection.addSeries(series1);

		final XYPlot xyplot = new XYPlot(seriesCollection, null,
				verticalAxis, renderer);
		// final XYPlot xyplot = new XYPlot((XYDataset)seriesCollection,
		// horizontalAxis, verticalAxis, renderer);

		xyplot.setDomainGridlinesVisible(true);
		xyplot.setRangeGridlinesVisible(true);
		xyplot.setRangeGridlinePaint(Color.black);
		xyplot.setDomainGridlinePaint(Color.black);

		final NumberAxis phaseAxis = new NumberAxis("Phase (Deg)");
		phaseAxis.setRange(new Range(-180, 180));
		phaseAxis.setTickUnit(new NumberTickUnit(30));
		final XYSeriesCollection seriesCollection2 = new XYSeriesCollection();
		seriesCollection2.addSeries(series2);
		final XYPlot xyplot2 = new XYPlot(seriesCollection2, null,
				phaseAxis, renderer2);

		// CombinedXYPlot combinedPlot = new CombinedXYPlot( horizontalAxis,
		// CombinedXYPlot.VERTICAL );
		CombinedDomainXYPlot combinedPlot = new CombinedDomainXYPlot(
				horizontalAxis);
		combinedPlot.add(xyplot, 1);
		combinedPlot.add(xyplot2, 1);
		combinedPlot.setGap(15.);

		// final JFreeChart chart = new JFreeChart(xyplot);
		final JFreeChart chart = new JFreeChart(combinedPlot);
		chart.setTitle(new TextTitle(plotTitle));

		// Here we need to see if test dir exists and create it if necessary ...
		try {
			// ChartUtilities.saveChartAsJPEG(new File("chart.jpg"), chart, 500,
			// 300);
			// ChartUtilities.saveChartAsPNG(outputFile, chart, 500, 300);
			ChartUtilities.saveChartAsPNG(outputFile, chart, 1000, 800);
		} catch (IOException e) {
			// System.err.println("Problem occurred creating chart.");
			logger.error("IOException:", e);
		}
	} // end plotResp

	private Boolean checkFileOut(File file) {

		// Check that dir either exists or can be created

		File dir = file.getParentFile();

		Boolean allIsOkay = true;

		if (dir.exists()) { // Dir exists --> check write permissions
			allIsOkay = dir.isDirectory() && dir.canWrite();
		} else { // Dir doesn't exist --> try to make it
			allIsOkay = dir.mkdirs();
		}

		if (!allIsOkay) { // We were unable to make output dir --> return false
			return false;
		}

		// Check that if file already exists it can be overwritten

		if (file.exists()) {
			if (!file.canWrite()) {
				return false;
			}
		}

		return true;
	}
}
