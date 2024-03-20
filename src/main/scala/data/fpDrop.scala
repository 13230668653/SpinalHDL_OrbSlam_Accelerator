package data

import dataStructure.FeaturePointOrb
import spinal.lib.experimental.chisel.Bundle
import dataStructure.{FeaturePoint, FeaturePointOrb}
import operator.NMSConfig
import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import wa.WaCounter
import operator._
import spinal.core.Component.push
import data._
import spinal.lib.experimental.chisel.Module
import utils.{ImageCount, ImageSize}


class FpDrop(SIZE_WIDTH : Int, DATA_WIDTH:Int, DEPTH:Int, BOUNDARY_SIZE:Int = 16) extends Module {
  val io = new Bundle{
    val sData = slave Stream new FeaturePointOrb(SIZE_WIDTH, DATA_WIDTH)
    val mData = master Stream new FeaturePointOrb(SIZE_WIDTH, DATA_WIDTH)
    val sizeIn = slave(new ImageSize(SIZE_WIDTH))
  }
  val sizeValid = io.sizeIn.sub(BOUNDARY_SIZE, BOUNDARY_SIZE).setAsReg()
  val leftInValid = io.sData.payload.size.colNum < BOUNDARY_SIZE
  val rightInValid = sizeValid.colNum > io.sData.payload.size.colNum
  val topInValid = io.sData.payload.size.rowNum < BOUNDARY_SIZE
  val downInValid = io.sData.payload.size.rowNum > io.sData.payload.size.rowNum

  val fifo = StreamFifo(io.sData.payload, DEPTH)
  io.sData.throwWhen(downInValid || topInValid || leftInValid || rightInValid) <> fifo.io.push
  fifo.io.pop <> io.mData
}

object FpDrop extends App {
  SpinalVerilog(new FpDrop(11, 8, 16, 16)).printPruned
}