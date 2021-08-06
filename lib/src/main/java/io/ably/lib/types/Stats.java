package io.ably.lib.types;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * A class encapsulating a Stats datapoint.
 * Ably usage information, across an account or an individual app,
 * is available as Stats records on a timeline with different granularities.
 * This class defines the Stats type and its subtypes, giving a structured
 * representation of service usage for a specific scope and time interval.
 * This class also contains utility methods to convert from the different
 * formats used for REST responses.
 */
public class Stats {

    /**
     * A breakdown of summary stats data for different (tls vs non-tls)
     * connection types.
     */
    public static class ConnectionTypes {
        public ResourceCount all;
        public ResourceCount plain;
        public ResourceCount tls;
    }

    /**
     * A datapoint for message volume (number of messages plus aggregate data size)
     */
    public static class MessageCount {
        public double count;
        public double data;
        public double uncompressedData;
    }

    public static class MessageCategory extends MessageCount {
        public Map<String, MessageCount> category;
    }

    /**
     * A breakdown of summary stats data for different (message vs presence)
     * message types.
     */
    public static class MessageTypes {
        public MessageCategory all;
        public MessageCategory messages;
        public MessageCategory presence;
    }

    /**
     * A breakdown of summary stats data for traffic over various transport types.
     */
    public static class MessageTraffic {
        public MessageTypes all;
        public MessageTypes realtime;
        public MessageTypes rest;
        public MessageTypes webhook;
    }

    /**
     * Aggregate data for numbers of requests in a specific scope.
     */
    public static class RequestCount {
        public double succeeded;
        public double failed;
        public double refused;
    }

    /**
     * Aggregate data for usage of a resource in a specific scope.
     */
    public static class ResourceCount {
        public double opened;
        public double peak;
        public double mean;
        public double min;
        public double refused;
    }

    public static class ProcessedCount {
        public double succeeded;
        public double skipped;
        public double failed;
    }

    public static class ProcessedMessages {
        public Map<String, ProcessedCount> delta;
    }

    public static class PushedMessages {
        public int messages;
        public Map<String, Integer> notifications;
        public int directPublishes;
    }

    public enum Granularity {
        minute,
        hour,
        day,
        month
    }

    private static String[] intervalFormatString = new String[] {
        "yyyy-MM-dd:hh:mm",
        "yyyy-MM-dd:hh",
        "yyyy-MM-dd",
        "yyyy-MM"
    };

    public static String toIntervalId(long timestamp, Granularity granularity) {
        String formatString = intervalFormatString[granularity.ordinal()];
        return new SimpleDateFormat(formatString).format(new Date(timestamp));
    }

    public static long fromIntervalId(String intervalId) {
        try {
            String formatString = intervalFormatString[0].substring(0, intervalId.length());
            return new SimpleDateFormat(formatString).parse(intervalId).getTime();
        } catch (ParseException e) { return 0; }
    }

    public String intervalId;
    public String unit;
    public int count;
    public String inProgress;
    public MessageTypes all;
    public MessageTraffic inbound;
    public MessageTraffic outbound;
    public MessageTypes persisted;
    public ConnectionTypes connections;
    public ResourceCount channels;
    public RequestCount apiRequests;
    public RequestCount tokenRequests;
    public PushedMessages push;
}
