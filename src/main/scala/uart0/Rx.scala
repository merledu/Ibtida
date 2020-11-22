package uart0
import chisel3._
import chisel3.util._

class Channel extends Bundle {
  val data = Input(Bits (8.W))
  val valid = Input(Bool ())
}

class Rx( frequency : Int , baudRate: Int) extends Module {
  val io = IO(new Bundle {
    val rxd = Input(Bits (1.W))
    val channel = Flipped(new Channel ())
  })
  val BIT_CNT = (( frequency + baudRate / 2) / baudRate - 1).U
  val START_CNT = ((3 * frequency / 2 + baudRate / 2) /
    baudRate - 1).U
  // Sync in the asynchronous RX data
  // Reset to 1 to not start reading after a reset
  val rxReg = RegNext(RegNext(io.rxd , 1.U), 1.U)
  val shiftReg = RegInit('A'.U(8.W))
  val cntReg = RegInit (0.U(20.W))
  val bitsReg = RegInit (0.U(4.W))
  val valReg = RegInit(false.B)
  when(cntReg =/= 0.U) {
    cntReg := cntReg - 1.U
  }. elsewhen (bitsReg =/= 0.U) {
    cntReg := BIT_CNT
    shiftReg := Cat(rxReg , shiftReg >> 1)
    bitsReg := bitsReg - 1.U
    // the last shifted in
    when(bitsReg === 1.U) {
      valReg := true.B
    }
    // wait 1.5 bits after falling edge of start
  }. elsewhen (rxReg === 0.U) {
    cntReg := START_CNT
    bitsReg := 8.U
  }
  when(valReg) {
    valReg := false.B
  }
  io.channel.data := shiftReg
  io.channel.valid := valReg
}
