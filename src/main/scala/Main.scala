import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateFunction
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.types.{DoubleType, StructField, StructType, TimestampType}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger


object StrategyTracking {
  final val BOOTSTRAPSERVERS = "127.0.0.1:9092"

  def main(args: Array[String]): Unit = {

    val mySchema = StructType(Array(
      StructField("ts", TimestampType),
      StructField("REFERENCE_PRICE", DoubleType),
      StructField("STRATEGY1", DoubleType),
      StructField("STRATEGY2", DoubleType),
      StructField("STRATEGY3", DoubleType)
    ))

    val spark = SparkSession
      .builder
      .appName("Strategy Tracking")
      .master("local")
      .getOrCreate()

    val fileStreamDf = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", BOOTSTRAPSERVERS)
      .option("startingOffsets", "latest")
      .option("subscribe", "security")
      .load()

    val df=fileStreamDf.select(col("value").cast("string")) .alias("csv").select("csv.*")
    val df_structured= df.selectExpr(
      "split(value,',')[0] as ts"
      ,"split(value,',')[1] as REFERENCE_PRICE"
      ,"split(value,',')[2] as STRATEGY1"
      ,"split(value,',')[3] as STRATEGY2"
      ,"split(value,',')[3] as STRATEGY3")
      .select(col("ts").cast(TimestampType),
        col("REFERENCE_PRICE").cast(DoubleType),
        col("STRATEGY1").cast(DoubleType),
        col("STRATEGY2").cast(DoubleType),
        col("STRATEGY3").cast(DoubleType)
      ).withColumn("SPREAD1", col("STRATEGY1")-col("REFERENCE_PRICE"))
      .withColumn("SPREAD2", col("STRATEGY2")-col("REFERENCE_PRICE"))
      .withColumn("SPREAD3", col("STRATEGY3")-col("REFERENCE_PRICE"))

    val rollingDF = df_structured.withWatermark("ts","5 seconds")
      .groupBy(window(col("ts"), "60 seconds"),
        col("ts"),
        col("STRATEGY1"),
        col("STRATEGY2"),
        col("STRATEGY3"),
        col("SPREAD1"),
        col("SPREAD2"),
        col("SPREAD3")
      ).count()
      //.agg(avg("SPREAD1") as "mean").
      //select("window.start", "window.end", "mean")

    val mean_df = rollingDF.groupBy("window")
      .agg(avg("SPREAD1") as "mean1", avg("SPREAD2") as "mean2", avg("SPREAD3") as "mean3",
        stddev_pop("spread1") as "std1", stddev_pop("spread2") as "std2",stddev_pop("spread3") as "std3")
      .select("window", "mean1","mean2","mean3","std1","std2","std3")

    val intermediate = rollingDF.join(mean_df, Seq("window"), "left")

    val rank_udf = udf((s1: Double, s2: Double,s3:Double) => {
      val b = List(("s1",s1),("s2",s2),("s3",s3)).sortBy(_._2)
      val res = b.map(m => m._1)
      res
    })

    val alert1 = intermediate
      .withColumn("alert1_s1", (abs(col("SPREAD1") - col("mean1")))/col("std1"))
      .withColumn("alert1_s2", (abs(col("SPREAD2") - col("mean2")))/col("std2"))
      .withColumn("alert1_s3", (abs(col("SPREAD3") - col("mean3")))/col("std3"))
      //.withColumn("percent", lit(uptimePct))
      .withColumn("rank", rank_udf(col("STRATEGY1"),col("STRATEGY2"), col("STRATEGY3")))

//
//    val uptimePct = (rollingDF.where(rollingDF("STRATEGY1").isNotNull).count()).toDouble/rollingDF.count
//    import spark.implicits._
//    val df = Seq("percent",uptimePct).toDF


    //
//
//    def cal_median(col:Column): Column ={
//
//    }

//    val median = df.stat.approxQuantile("SPREAD1", Array(0.5), 0.25)(0)
//
//    val median_df = rollingDF.groupBy("window").agg(cal_median(col("SPREAD1") as "median1")

//    val alert2 = alert1
//      .withColumn("alert2_s1", (abs(col("SPREAD1") - col("median1")))/col("mad1"))
//      .withColumn("alert2_s2", (abs(col("SPREAD2") - col("median2")))/col("mad2"))
//      .withColumn("alert2_s3", (abs(col("SPREAD3") - col("median3")))/col("mad3"))

 //   val res_alert = alert2.select("window","alert1_s1","alert1_s2","alert1_s3", "alert2_s1","alert2_s2","alert2_s3")



//    val res_df = res_alert
//      .withColumn("flag1_s1", col("alert1_s1")>3.5)
//      .withColumn("flag1_s2", col("alert1_s1")>3.5)
//      .withColumn("flag1_s3", col("alert1_s1")>3.5)
//      .withColumn("flag2_s1", col("alert1_s1")>3.5)
//      .withColumn("flag2_s2", col("alert1_s1")>3.5)
//      .withColumn("flag2_s3", col("alert1_s1")>3.5)
//      .withColumn("rank" , rank_udf(col("STRATEGY1"), col("STRATEGY2"), col("STRATEGY3")))


    val query = alert1.writeStream
      .format("parquet")
      .trigger(Trigger.ProcessingTime("10 seconds"))
      .option("checkpointLocation", "checkpoint/")
      .start("output/")

    query.awaitTermination()
  }
}
