case class InputRow(SECURITYID:String,
                    ts:java.sql.Timestamp,
                    STRATEGY1:java.lang.Double,
                    SPREAD1: java.lang.Double,
                    STRATEGY2:java.lang.Double,
                    SPREAD2: java.lang.Double,
                    STRATEGY3:java.lang.Double,
                    SPREAD3: java.lang.Double)

case class StrategyState(SECURITYID:String,
                         var windowStart: java.sql.Timestamp,
                         var windowEnd: java.sql.Timestamp,
                         var windowMean1:java.lang.Double,
                         var windowStd1:java.lang.Double,
                         var windowMedian1:java.lang.Double,
                         var windowMad1:java.lang.Double,
                         var windowList1:List[InputRow],
                         var s1Count:java.lang.Integer,
                         var totalCount:java.lang.Integer
                        )

case class StrategyOutput(SECURITYID:String,
                          var ts: java.sql.Timestamp,
                          var rank: List[String],
                          var S1_uptimePct: Double,
                          var S1_alert1:Option[Boolean],
                          var S1_alert2:Option[Boolean]
                         )