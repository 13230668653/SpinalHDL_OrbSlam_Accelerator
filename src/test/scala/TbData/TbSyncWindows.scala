package TbData

import data.{Windows, WindowsConfig, syncWindows}
import spinal.core._
import spinal.core.sim._
import scala.util.Random

import java.io.{File, PrintWriter}
import scala.io.Source
//测试通过 2 1 下一次测试，每五行删除一行数据
class TbSyncWindows(config : WindowsConfig) extends syncWindows (config) {
    val random = new Random()
    val ValidRandomTest = true
    fsm.colCnt.count.simPublic()
    fsm.rowCnt.count.simPublic()
    def toHexString(width: Int, b: BigInt): String = {
        var s = b.toString(16)
        if (s.length < width) {
            s = "0" * (width - s.length) + s
        }
        s
    }

    def init = {
        clockDomain.forkStimulus(5)

        io.sData.valid #= false
        io.sData.payload #= 0
        io.mData.ready #= false
        io.start #= false

        io.rowNumIn #= 160
        io.colNumIn #= 20
        clockDomain.waitSampling(10)
    }

    def in(src: String): Unit = {
        fork {
            for (line <- Source.fromFile(src).getLines) {//产生随机的拉低数据
                io.sData.payload #= BigInt(line.trim, 16)
                io.sData.valid #= true
                clockDomain.waitSamplingWhere(io.sData.ready.toBoolean)
                if(ValidRandomTest){
                    io.sData.valid #= false
                    var randomNumber = random.nextInt(100)
                    if (randomNumber > 5) {
                        randomNumber = 0
                    }
                    clockDomain.waitSampling(randomNumber)
                }
            }
        }
    }

    def out(dst_scala: String, dst: String): Unit = {
        clockDomain.waitSampling()
        val testFile = new PrintWriter(new File(dst_scala))
        val dstFile = Source.fromFile(dst).getLines().toArray
        val total = dstFile.length
        var error = 0
        var iter = 0
        var i = 0
        while (i < dstFile.length) {
            clockDomain.waitSampling()
            //io.mData.ready #= true //不使用反压功能
            io.mData.ready.randomize() //使用反压功能
            if (io.mData.valid.toBoolean && io.mData.ready.toBoolean) {
                //接收整个窗口的数据并进行校验
                i = i + config.WINDOWS_SIZE_H * config.WINDOWS_SIZE_W
                io.start #= false
                for(h <- 0 until config.WINDOWS_SIZE_H){
                    for (w <- 0 until config.WINDOWS_SIZE_W) {
                        val temp = dstFile(iter)
                        val o = toHexString(2 * config.DATA_NUM, io.mData.payload(h)(w).toBigInt)
                        if (!temp.equals(o)) {
                            error = error + 1
                            printf(i + "\n");
                            printf("row:%d, col:%d\n", fsm.rowCnt.count.toInt, fsm.colCnt.count.toInt)
                            printf("h:%d, w:%d\n", h, w)
                            if(error > 20){
                                i+=999999
                            }
                            //return ;
                        }
                        if (iter % 1000 == 0) {
                            val errorP = error * 100.0 / total
                            println(s"total iter = $total current iter =  $iter :::  error count = $error error percentage = $errorP%")
                        }
                        testFile.write(o + "\r\n")
                        iter = iter + 1
                    }
                }
            }
        }
        if(error>0){
            println(s"error is $error\n")
        } else{
            println(s"ac\n")
        }

        sleep(100)
        testFile.close()
        simSuccess()
    }
}

object TbSyncWindows extends App {
    val spinalConfig = new SpinalConfig(
        defaultClockDomainFrequency = FixedFrequency(200 MHz),
        defaultConfigForClockDomains = ClockDomainConfig(resetActiveLevel = HIGH, resetKind = SYNC)
    )
    val dataGenerateRow31Config = WindowsConfig(DATA_NUM = 8, WINDOWS_SIZE_H = 31, WINDOWS_SIZE_W = 5,
        MEM_DEPTH = 128, SIZE_WIDTH = 11)

    //SimConfig.withXSim.withWave.withConfig(spinalConfig).compile(new TbMaxPooling()).doSimUntilVoid { dut =>
    SimConfig.withWave.withConfig(spinalConfig).compile(new TbSyncWindows(dataGenerateRow31Config)).doSimUntilVoid { dut =>
        dut.init
        dut.io.start #= true
        val path = "F:\\TestData\\OpencvData\\Windows"
        dut.in(path + "\\ReferenceDataIn.txt")
        dut.out(path + "\\dstDataOut.txt",path + "\\ReferenceDataOut.txt")
    }
}