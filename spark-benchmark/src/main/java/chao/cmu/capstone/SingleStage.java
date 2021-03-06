package chao.cmu.capstone;

import kafka.serializer.StringDecoder;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaPairInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka.KafkaUtils;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import scala.Tuple2;

import java.util.HashMap;
import java.util.HashSet;

public class SingleStage {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: <brokers> <topic> <func>");
            System.exit(1);
        }

        String brokers = args[0];
        String topic = args[1];
        String func = args[2];

        SparkConf sparkConf = new SparkConf()
                .setAppName("spark-benchmark-" + func)
                .set("spark.streaming.backpressure.enabled", "true")
                .set("spark.streaming.ui.retainedBatches", "300");
        JavaStreamingContext jssc = new JavaStreamingContext(sparkConf, Durations.seconds(1));

        HashSet<String> topicSet = new HashSet<>();
        topicSet.add(topic);

        HashMap<String, String> kafkaParams = new HashMap<>();
        kafkaParams.put("bootstrap.servers", brokers);
        kafkaParams.put("auto.offset.reset", "largest");

        JavaPairInputDStream<String, String> messages = KafkaUtils.createDirectStream(
                jssc, String.class, String.class, StringDecoder.class, StringDecoder.class, kafkaParams, topicSet);

        JavaDStream<Long> counts;
        if (func.startsWith("p")) {
            counts = messages.map(new Function<Tuple2<String, String>, String>() {
                @Override
                public String call(Tuple2<String, String> tuple2) {
                    JSONObject object = (JSONObject) JSONValue.parse(tuple2._2());
                    String text = (String)object.get("text");
                    return text;
                }
            }).count();
        } else if (func.startsWith("s")) {
            counts = messages.filter(new Function<Tuple2<String, String>, Boolean>() {
                int i = 0;

                @Override
                public Boolean call(Tuple2<String, String> tuple2) throws Exception {
                    Boolean ret = i % 10 == 0;
                    ++i;
                    return ret;
                }
            }).count();
        } else if (func.startsWith("f")) {
            counts = messages.filter(new Function<Tuple2<String, String>, Boolean>() {
                @Override
                public Boolean call(Tuple2<String, String> tuple2) throws Exception {
                    JSONObject object = (JSONObject) JSONValue.parse(tuple2._2());
                    return "en".equals(object.get("lang"));
                }
            }).count();
        } else if (func.startsWith("v")) {
            counts = messages.count();
        } else {
            counts = messages.map(new Function<Tuple2<String, String>, String>() {
                @Override
                public String call(Tuple2<String, String> tuple2) {
                    return tuple2._2();
                }
            }).count();
        }
        counts.print();
        jssc.start();
        jssc.awaitTermination();
    }
}
