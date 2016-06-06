package asl.seedscan.metrics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Hashtable;
import java.util.List;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asl.metadata.Channel;
import asl.metadata.meta_new.ChannelMeta.ResponseUnits;
import asl.metadata.meta_new.ChannelMetaException;
import asl.seedscan.event.EventCMT;
import asl.util.PlotMaker2;
import asl.util.PlotMakerException;
import asl.util.Trace;
import asl.util.TraceException;
import edu.sc.seis.TauP.SphericalCoords;
import sac.SacHeader;
import sac.SacTimeSeries;
import timeutils.MyFilter;



/**
 * <p>
 * The Class EventCompareSynthetic.
 * </p>
 * 
 * The difference is calculated by a power scale formula result=SUM(data[i] *
 * syn[i]) / SUM(syn[i] * syn[i])
 * <p>
 * Result meanings<br>
 * x = 0 data is to small or all 0s. The channel is dead.<br>
 * {@literal 0 < x < 1} data shows less displacement than the synthetic.<br>
 * 1 = data aligns exactly with the synthetic.<br>
 * {@literal x > 1} data shows greater displacement than the synthetic.<br>
 * {@literal x < 0} The data is out of phase from the synthetic.<br>
 * x = -1 The data is exactly 180 degrees out of phase, but matches the data.<br>
 * </p>
 * 
 * <a href="http://srl.geoscienceworld.org/content/77/1/12.full">Observations of
 * Time-dependent Errors in Long-period Instrument Gain at Global Seismic
 * Stations</a>Equation 3
 */
public class EventCompareSynthetic extends Metric {

	/** The Constant logger. */
	private static final Logger logger = LoggerFactory
			.getLogger(asl.seedscan.metrics.EventCompareSynthetic.class);

	/** The Constant PERIOD1. */
	private static final double PERIOD1 = 500;

	/** The Constant PERIOD2. */
	private static final double PERIOD2 = 400;

	/** The Constant PERIOD3. */
	private static final double PERIOD3 = 165;

	/** The Constant PERIOD4. */
	private static final double PERIOD4 = 85;

	/** The Constant f1. Period 1 frequency: 1./PERIOD1 */
	private static final double f1 = 1. / PERIOD1;

	/** The Constant f2. Period 2 frequency: 1./PERIOD2 */
	private static final double f2 = 1. / PERIOD2;

	/** The Constant f3. Period 3 frequency: 1./PERIOD3 */
	private static final double f3 = 1. / PERIOD3;

	/** The Constant f4. Period 4 frequency: 1./PERIOD4 */
	private static final double f4 = 1. / PERIOD4;

	/** The event CMTs. */
	private Hashtable<String, EventCMT> eventCMTs = null;

	/** The channels. */
	private Channel[] channels = null;

	/** The sac header. */
	private SacHeader hdr = null;

	/**
	 * @see asl.seedscan.metrics.Metric#getVersion()
	 */
	@Override
	public long getVersion() {
		return 2;
	}

	/**
	 * @see asl.seedscan.metrics.Metric#getName()
	 */
	@Override
	public String getName() {
		return "EventCompareSynthetic";
	}

	/**
	 * @see asl.seedscan.metrics.Metric#process()
	 */
	public void process() {
		logger.info("-Enter- [ Station {} ] [ Day {} ]", getStation(), getDay());

		eventCMTs = getEventTable();
		if (eventCMTs == null) {
			logger.info(
					String.format("No Event CMTs found for Day=[%s] --> Skip EventCompareSynthetic Metric", getDay()));
			return;
		}

		List<Channel> channels = stationMeta.getRotatableChannels();
		String[] basechannel;
		String basePreSplit = null;
		List<String> bands;
		String preSplitBands = null;
		try {
			basePreSplit = get("base-channel");

		} catch (NoSuchFieldException ignored) {
		}
		try {
			preSplitBands = get("channel-restriction");
		} catch (NoSuchFieldException ignored) {
		}
		if (basePreSplit == null) {
			basePreSplit = "XX-LX";
			logger.info("No base channel for EventCompare Strong Motion using: " + basePreSplit);
		}
		if (preSplitBands == null) {
			preSplitBands = "LH";
			logger.info("No band restriction set for EventCompare Strong Motion using: " + preSplitBands);
		}

		bands = Arrays.asList(preSplitBands.split(","));
		basechannel = basePreSplit.split("-");

		for (Channel curChannel : channels) {

			String channelVal = curChannel.toString().split("-")[1];
			if (bands.contains(channelVal.substring(0, 2))) {
				ByteBuffer digest = metricData.valueDigestChanged(curChannel, createIdentifier(curChannel),
						getForceUpdate());
				if (digest == null)
					continue; // Skip since not out of date or missing.

				double result = 0;
				boolean correlated = false;

				int nEvents = 0;
				// Loop over Events for this day
				try { // getFilteredDisplacement
					SortedSet<String> eventKeys = new TreeSet<String>(eventCMTs.keySet());
					for (String key : eventKeys) {
						Hashtable<String, SacTimeSeries> synthetics = getEventSynthetics(key);
						if (synthetics == null) {
							logger.info("== {}: No synthetics found for key=[{}] for this station\n", getName(), key);
							continue;
						}

						SacTimeSeries sacSynthetics = null;
						String fileKey = getStn() + "." + basechannel[0] + "." + basechannel[1].substring(0, 2)
								+ channelVal.substring(2, 3) + ".modes.sac.proc";
						// e.g. "ANMO.XX.LXZ.modes.sac.proc"
						if (synthetics.containsKey(fileKey)) {
							sacSynthetics = synthetics.get(fileKey);
							MyFilter.bandpass(sacSynthetics, f1, f2, f3, f4);
						} else {
							logger.info("Did not find sac synthetic=[{}] in Hashtable", fileKey);
							continue; // Try next event
						}

						SacHeader header = sacSynthetics.getHeader();

						long eventStartTime = getSacStartTimeInMillis(header);

						/*
						 * Window the data from the Event (PDE) Origin. Use
						 * larger time window to do the instrument decons and
						 * trim it down later:
						 */

						long duration = 8000000L; // 8000 sec = 8000000 msecs
						long eventEndTime = eventStartTime + duration;

						// Window to use for comparisons
						int nstart = 0;
						int nend = header.getNpts() - 1;

						ResponseUnits units = ResponseUnits.DISPLACEMENT;

						double[] baseData = sacArrayToDouble(sacSynthetics);
						double[] channelData = metricData.getFilteredDisplacement(units, curChannel, eventStartTime,
								eventEndTime, f1, f2, f3, f4);
						double corrVal = getCorr(channelData, baseData, nstart, nend);
						if (Math.abs(corrVal) >= 0.85) {
							correlated = true;
							double tempResult = calcDiff(channelData, baseData, nstart, nend);
							if (tempResult >= 50.0)
								tempResult = 50.0;

							result += tempResult;
							nEvents++;
						}
					}
					if (correlated) {
						metricResult.addResult(curChannel, result / nEvents, digest);
					} else {
						logger.info("station=[{}] day=[{}]: Low correlation", getStation(), getDay());
					}

				} catch (ChannelMetaException e) {
					logger.error("ChannelMetaException:", e);
				} catch (MetricException e) {
					logger.error("MetricException:", e);
				}
			}

		}
	}

	/**
	 * Gets the sac start time in millis.
	 * 
	 * @param hdr
	 *            the sac header
	 * @return the sac start time in millis
	 */
	private long getSacStartTimeInMillis(SacHeader hdr) {
		GregorianCalendar gcal = new GregorianCalendar(
				TimeZone.getTimeZone("GMT"));
		gcal.set(Calendar.YEAR, hdr.getNzyear());
		gcal.set(Calendar.DAY_OF_YEAR, hdr.getNzjday());
		gcal.set(Calendar.HOUR_OF_DAY, hdr.getNzhour());
		gcal.set(Calendar.MINUTE, hdr.getNzmin());
		gcal.set(Calendar.SECOND, hdr.getNzsec());
		gcal.set(Calendar.MILLISECOND, hdr.getNzmsec());

		return gcal.getTimeInMillis();
	}

	/**
	 * Sac array to double.
	 * 
	 * @param sacArray
	 *            the sac array
	 * @return the array list
	 */
	private double[] sacArrayToDouble(SacTimeSeries sac) {
		
			float[] fdata = sac.getY();
			double[] data = new double[fdata.length];
			for (int k = 0; k < fdata.length; k++) {
				data[k] = (double) fdata[k];
			}

		return data;
	}

	/**
	 * Compare 2 double[] arrays between array indices n1 and n2 currently
	 * doing: SUM[ x(n) * y(n) ] / SUM[ y(n) * y(n) ], where x(n)=data and
	 * y(n)=synth
	 * 
	 * {@literal difference = 0. -->} data are all zero<br>
	 * {@literal difference = 1. -->} data exactly matches synthetic<br>
	 * {@literal difference = 1. -->} data exactly matches -synthetic (is 180 deg out of
	 * phase)<br>
	 * 
	 * <a href="http://srl.geoscienceworld.org/content/77/1/12.full">http://srl.
	 * geoscienceworld.org/content/77/1/12.full</a> Equation 3 <br>
	 * 
	 * 
	 * data1 = x, data2 = y
	 * 
	 * @param data1
	 *            the data in meters displaced
	 * @param data2
	 *            the synthetic in meters displaced
	 * @param n1
	 *            the window start; nstart in process()
	 * @param n2
	 *            the window end; nend in process()
	 * @return the result
	 */
	private double calcDiff(double[] data1, double[] data2, int n1, int n2) {
		if (n2 < n1) {
			logger.error(
					"station=[{}] day=[{}]: calcDiff: n2 < n1 --> Bad window",
					getStation(), getDay());
			return NO_RESULT;
		}
		if (n2 >= data1.length || n2 >= data2.length) {
			logger.error(
					"station=[{}] day=[{}]: calcDiff: n2=[{}] > data1.length=[{}] and/or data2.length=[{}] --> Bad window",
					getStation(), getDay(), n2, data1.length, data2.length);
			return NO_RESULT;
		}
		double numerator = 0.;
		double denomenator = 0.;

		for (int i = n1; i < n2; i++) {
			numerator += data1[i] * data2[i];
			denomenator += data2[i] * data2[i];
		}

		if (denomenator == 0.) {
			logger.error(
					"station=[{}] day=[{}]: calcDiff: denomenator==0 --> Divide by 0 --> Expect result = Infinity!",
					getStation(), getDay());
		}
		double result = numerator / denomenator;
        if (result > 4.){
            result = 4.;
        }

		return result;
	}


	private double getCorr(double[] data1, double[] data2, int n1, int n2){
		//This function computs the Pearson's correlation value for the two time series
		if (n2 < n1) {
			logger.error(
					"station=[{}] day=[{}]: calcDiff: n2 < n1 --> Bad window",
					getStation(), getDay());
			return NO_RESULT;
		}
		if (n2 >= data1.length || n2 >= data2.length) {
			logger.error(
					"station=[{}] day=[{}]: calcDiff: n2=[{}] > data1.length=[{}] and/or data2.length=[{}] --> Bad window",
					getStation(), getDay(), n2, data1.length, data2.length);
			return NO_RESULT;
		}


		//Calculate the mean of both data streams
		double data1mean = 0.;
		double data2mean = 0.;		

		for (int i = n1; i < n2; i++) {
			data1mean += data1[i];
			data2mean += data2[i];

		}  
		data1mean = data1mean / (double) data1.length;
		data2mean = data2mean / (double) data2.length;

		//Calculate the standard deviation of both data streams
		double std1 = 0.;
		double std2 = 0.;

		for (int i = n1; i < n2; i++){
			std1 += (data1[i] - data1mean) * (data1[i] - data1mean);
			std2 += (data2[i] - data2mean) * (data2[i] - data2mean);

		}
		std1 = std1 / (double) data1.length;
		std2 = std2 / (double) data2.length;

		//Calculate the r correlation
		double r = 0.;
		for (int i = n1; i < n2; i++) {
			r += (data1[i] - data1mean) * (data2[i] - data2mean) / (std1*std2);

		}
		r = r / (double) (data1.length - 1);
		
		return r;
	}

	/**
	 * Make plots.
	 * 
	 * @param d00
	 *            the 00 data
	 * @param d10
	 *            the 10 data
	 * @param d20
	 *            the 20 data
	 * @param nstart
	 *            the window start; nstart in process()
	 * @param nend
	 *            the window end; nend in process()
	 * @param key
	 *            the key
	 * @param eventNumber
	 *            the event number
	 * @throws PlotMakerException
	 *             the plot maker exception
	 * @throws TraceException
	 *             the trace exception
	 */
	public void makePlots(ArrayList<double[]> d00, ArrayList<double[]> d10,
			ArrayList<double[]> d20, int nstart, int nend, String key,
			int eventNumber) throws PlotMakerException, TraceException {
		PlotMaker2 plotMaker = null;
		EventCMT eventCMT = eventCMTs.get(key);
		double evla = eventCMT.getLatitude();
		double evlo = eventCMT.getLongitude();
		double stla = stationMeta.getLatitude();
		double stlo = stationMeta.getLongitude();
		double gcarc = SphericalCoords.distance(evla, evlo, stla, stlo);

		final String plotTitle = String.format(
				"[ Event: %s ] [ Station: %s ] [ Dist: %.2f ] %s", key,
				getStation(), gcarc, getName());

		final String pngName = String.format("%s.synthcompare.ev-%d.png",
				getOutputDir(), eventNumber);

		if (plotMaker == null) {
			plotMaker = new PlotMaker2(plotTitle);
			plotMaker.initialize3Panels("LHZ", "LH1/LHN", "LH2/LHE");
		}

		BasicStroke stroke = new BasicStroke(2.0f);

		int npts = nend - nstart + 1;

		double[] xsecs = new double[npts];
		for (int k = 0; k < xsecs.length; k++) {
			xsecs[k] = (float) (k + nstart); // hard-wired for LH? dt=1.0
		}

		try { // addTraceToPanel()
			if (d00 != null) {
				for (int i = 0; i < d00.size(); i++) {
					double[] dataIn = d00.get(i);
					double[] dataOut = new double[npts];
					System.arraycopy(dataIn, nstart, dataOut, 0, npts);
					plotMaker.addTraceToPanel(new Trace(xsecs, dataOut,
							channels[i].toString(), Color.green, stroke), i);
				}
			}
			if (d10 != null) {
				for (int i = 0; i < d10.size(); i++) {
					double[] dataIn = d10.get(i);
					double[] dataOut = new double[npts];
					System.arraycopy(dataIn, nstart, dataOut, 0, npts);
					plotMaker.addTraceToPanel(new Trace(xsecs, dataOut,
							channels[i + 3].toString(), Color.red, stroke), i);
				}
			}
			if (d20 != null) {
				for (int i = 0; i < d20.size(); i++) {
					double[] dataIn = d20.get(i);
					double[] dataOut = new double[npts];
					System.arraycopy(dataIn, nstart, dataOut, 0, npts);
					// Convert synthetic channel XX-LHN to XX-LHND for plot
					// legend
					String kChan = channels[i + 6].toString();
					if (channels[i + 6].toString().contains("LHN")
							|| channels[i + 6].toString().contains("LHE")) {
						kChan += "D";
					}
					plotMaker.addTraceToPanel(new Trace(xsecs, dataOut, kChan,
							Color.black, stroke), i);
				}
			}

			plotMaker.writePlot(pngName);
		} catch (PlotMakerException e) {
			throw e;
		} catch (TraceException e) {
			throw e;
		}
	}
}
