package TbTop

import TbTop.TbResize.spinalConfig
import operator.operator.dataGenerateImage
import operator.{GaussianBlur, GaussianBlurConfig}
import spinal.core._
import spinal.core.sim._

import java.io.{File, PrintWriter}
import scala.io.Source
import top._

import scala.collection.mutable.ArrayBuffer
import scala.util.Random
case class TbFast(config : FastConfig) extends Fast(config){
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
    //io.sData.payload #= 0
    io.start #= false
    io.rowNumIn #= 480
    io.colNumIn #= 640 >> 3
    io.threshold #= 40
    io.mData.ready #= false
    clockDomain.waitSampling(10)
  }

  def in(src: String): Unit = {
    val random = new Random()
    fork {
      for (line <- Source.fromFile(src).getLines) {
        //io.sData.payload #= BigInt(line.trim, 16)
        io.sData.valid #= true
        clockDomain.waitSamplingWhere(io.sData.ready.toBoolean)
        io.sData.valid #= false
        val randomInt = random.nextInt(25)
        if (randomInt < 4) clockDomain.waitSampling(randomInt)
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
      io.mData.ready #= true
      if (io.mData.valid.toBoolean && io.mData.ready.toBoolean) {
        io.start #= false
        val temp = dstFile(iter)
        val colNum = io.mData.payload.colNum.toInt * 8 + io.mData.payload.selNum.toInt
        val rowNum = io.mData.payload.rowNum.toInt
        val data = io.mData.payload.dataPoint.toInt
        val o = toHexString(4, colNum) + toHexString(4, rowNum) + toHexString(2, data)

        if (!temp.equals(o)) {
          error = error + 1
          if(error < 100){
            printf(i + "\n");
          }
        }
        i = i + 1
        if (iter % 1000 == 0) {
          val errorP = error * 100.0 / total
          println(s"total iter = $total current iter =  $iter :::  error count = $error error percentage = $errorP%")
        }
        testFile.write(o + "\r\n")
        iter = iter + 1
      }
    }
    if (error > 0) {
      println(s"error is $error\n")
    } else {
      println(s"ac\n")
    }

    sleep(100)
    testFile.close()
    simSuccess()
  }
}

case class TbORB_Compute(config : ORB_ComputeConfig) extends ORB_Compute(config){
  def toHexString(width: Int, b: BigInt): String = {
    var s = b.toString(16)
    if (s.length < width) {
      s = "0" * (width - s.length) + s
    }
    s
  }

  var finishResize = false
  var finishFpRs = false

  def matchMask(x: Int) : Int = {x match {
    case 0 => 0x00e0 //0000 0000 1110 0000
    case 7 => 0x0070 //0000 0000 0111 0000
    case 6 => 0x0038 //0000 0000 0011 1000
    case 5 => 0x001c //0000 0000 0001 1100
    case 4 => 0x000e //0000 0000 0000 1110
    case 3 => 0x0007 //0000 0000 0000 0111
    case 2 => 0x8003 //1000 0000 0000 0011
    case 1 => 0xc001 //1100 0000 0000 0001
  }}

  def init(row:Int,col:Int,threshold:Int) = {
    clockDomain.forkStimulus(5000)
    io.sData.valid #= false
    io.sData.payload #= 0
    io.start #= false

    //sizeIn
    io.sizeIn.rowNum #= row - 1
    io.sizeIn.colNum #= ((col+7) >> 3) - 1

    //--------------------resize----------------------------//
    //这是 经过位宽转换后输入的大小
    io.colNumFlilterIn #= ((col + 9) / 10) - 1
    //这个是真正的有效数据输入个数
    io.rowNumSrcIn #= row - 1
    io.colNumSrcIn #= ((col + 8) / 10) - 1

    var dcol = ((col / 5) * 4)
    if((col % 5) != 0)
      dcol = ((col / 5) * 4 + (col % 5) - 1)


    if(dcol % 8 == 0){
      io.maskR #= BigInt("00000000", 2)
    } else {
      io.maskR #= (~((1 << (dcol % 8)) - 1)) & 0xff
    }
    //--------------------resize----------------------------//

    //--------------------gauss----------------------------//
    if(col % 8 == 0){
      io.inValid #= 7
      io.maskG #= 0x00
    } else {
      io.inValid #= (col % 8) - 1
      io.maskG #= (~((1 << (col % 8)) - 1)) & 0xff
    }
    //--------------------gauss----------------------------//

    //--------------------fast----------------------------//
    io.threshold #= threshold
    io.maskF #= matchMask(col % 8)
    //--------------------fast----------------------------//
    io.mData.ready #= false
    io.mDataImage.ready #= false
    clockDomain.waitSampling(10)
  }

  def in(src: String): Unit = {
    val random = new Random()
    fork {
      for (line <- Source.fromFile(src).getLines) {
        io.sData.payload #= BigInt(line.trim, 16)
        io.sData.valid #= true
        clockDomain.waitSamplingWhere(io.sData.ready.toBoolean)
        io.sData.valid #= false
        val randomInt = random.nextInt(25)
        if (randomInt < 4) clockDomain.waitSampling(randomInt)
      }
    }
  }

  def outResize(dst_scala: String, dst: String): Unit = {
    fork{
      clockDomain.waitSampling()
      val testFile = new PrintWriter(new File(dst_scala))
      val dstFile = Source.fromFile(dst).getLines().toArray
      val total = dstFile.length
      var error = 0
      var iter = 0
      var i = 0
      while (i < dstFile.length) {
        clockDomain.waitSampling()
        //            io.mData.ready #= true
        io.mDataImage.ready.randomize()
        if (io.mDataImage.valid.toBoolean && io.mDataImage.ready.toBoolean) {
          io.start #= false
          val temp = dstFile(iter)
          val o = toHexString(16, io.mDataImage.payload.toBigInt)
          if (!temp.equals(o)) {
            error = error + 1
            if(error < 100){
              printf(i + ":" + o + "\n")
            }
          }
          i = i + 1
          if (iter % 1000 == 999 || iter == (total - 1)) {
            val errorP = error * 100.0 / total
            println(s"Resize : total iter = $total current iter =  $iter :::  error count = $error error percentage = $errorP%")
          }
          testFile.write(o + "\r\n")
          iter = iter + 1
        }
      }
      if (error > 0) {
        println(s"Resize error is $error\n")
      } else {
        println(s"Resize ac\n")
      }
      sleep(100)
      clockDomain.waitSampling(1000)
      testFile.close()
      finishResize = true
    }
  }

  def outFpRs(dst_scala: String, dst: String): Unit = {
    fork {
      clockDomain.waitSampling()
      val testFile = new PrintWriter(new File(dst_scala))
      val dstFile = Source.fromFile(dst).getLines().toArray
      val total = dstFile.length
      var error = 0
      var iter = 0
      var i = 0
      while (i < dstFile.length) {
        clockDomain.waitSampling()
        //            io.mData.ready #= true
        io.mData.ready.randomize()
        if (io.mData.valid.toBoolean && io.mData.ready.toBoolean) {
          io.start #= false
          val temp = dstFile(iter)
          //          val o = toHexString(16, io.mData.payload.fp)

          val colNum = io.mData.payload.fp.size.colNum.toInt
          val rowNum = io.mData.payload.fp.size.rowNum.toInt
          val score = io.mData.payload.fp.score.toInt
          val data = io.mData.payload.rs.toBigInt

          val o = toHexString(4, colNum) + toHexString(4, rowNum) + toHexString(2, score) + toHexString(32, data)

          if (!temp.equals(o)) {
            error = error + 1
            if (error < 100) {
              printf(i + ":" + o + "\n")
            }
          }
          i = i + 1
          if (iter % 1000 == 999 || iter == (total - 1)) {
            val errorP = error * 100.0 / total
            println(s"FpRs : total iter = $total current iter =  $iter :::  error count = $error error percentage = $errorP%")
          }
          testFile.write(o + "\r\n")
          iter = iter + 1
        }
      }
      if (error > 0) {
        println(s"FpRs error is $error\n")
      } else {
        println(s"FpRs ac\n")
      }

      sleep(100)
      clockDomain.waitSampling(1000)
      testFile.close()
      finishFpRs = true
    }
  }

  def waitSuccess(): Unit ={
    while(!finishFpRs || !finishResize){
      clockDomain.waitSampling(1000)
    }
    clockDomain.waitSampling(10000)
    simSuccess()
  }

}

object TbORB_Compute extends App {
  val spinalConfig = new SpinalConfig(
    defaultClockDomainFrequency = FixedFrequency(200 MHz),
    defaultConfigForClockDomains = ClockDomainConfig(resetActiveLevel = HIGH, resetKind = SYNC)
  )
  //SimConfig.withXSim.withWave.withConfig(spinalConfig).compile(new TbMaxPooling()).doSimUntilVoid { dut =>
  SimConfig.withXilinxDevice("xq7vx690trf1157-2I").withXSimSourcesPaths(ArrayBuffer("src/test/ip"), ArrayBuffer("")).withWave.withXSim.withConfig(spinalConfig).compile(new TbORB_Compute(ORB_ComputeConfig())).doSimUntilVoid { dut =>
    dut.init(480, 640, 20)
    dut.io.start #= true
    dut.clockDomain.waitSampling(10)
    val path = "C:\\myData\\data\\xsim_data\\slam\\Fast640480"

    dut.in(path + "\\SourceDataIn.txt")
    dut.outResize(path + "\\simDataout.coe",path + "\\opencvDataOutFp.coe")
    dut.outFpRs(path + "\\simDataout.coe",path + "\\opencvDataOutFp.coe")
    dut.waitSuccess()
  }
}