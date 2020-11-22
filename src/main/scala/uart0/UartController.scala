package uart0
import chisel3._
import chisel3.util._

class UartController(frequency: Int, baudRate: Int) extends Module {
  val io = IO(new Bundle {
    /**
     * isStalled input is coming in from the soc telling whether the core is stalled or not.
     * When this signal is true it means the core is stalled and we need to start the UART communication. */
    val isStalled = Input(Bool())

    val rx_data_o = Output(UInt(32.W))
    val addr_o = Output(UInt(14.W))
    val rxd = Input(UInt(1.W))

    val valid = Output(Bool())

    /**
     * done is the control signal which is attached with FetchBusController.
     * It tells the FetchBusController that the UART is done writing to memory
     * On the basis of this signal the FetchBusController will remove the stall.*/
    val done = Output(Bool())
  })

  val regDone = RegInit(false.B)
  val count = RegInit(0.U(3.W))
  val regFinalData = RegInit(0.U(32.W))
  val regAddr = RegInit(16383.U(14.W))
  val regValid = RegInit(false.B)

  val rx = Module(new Rx(frequency, baudRate))
  rx.io.rxd := io.rxd

  val dataReg = RegInit(0.U(8.W))
  val regLSB1 = RegInit(0.U(8.W))
  val regLSB2 = RegInit(0.U(8.W))
  val regMSB1 = RegInit(0.U(8.W))
  val regMSB2 = RegInit(0.U(8.W))

  when(io.isStalled && !regDone) {
    when(rx.io.channel.valid === 1.U) {
      // We get 1 byte of data from the Rx module
      // dataReg := rx.io.channel.bits
      dataReg := rx.io.channel.data
      count := count + 1.U
      regValid := false.B
    }
  }
  switch(count) {
    is(1.U) {
      regLSB1 := dataReg
    }
    is(2.U) {
      regLSB2 := dataReg
    }
    is(3.U) {
      regMSB1 := dataReg
    }
    is(4.U) {
      // Now the 32-Bit data is complete here
      val data = Cat(dataReg, regMSB1, regLSB2, regLSB1)
      when(data === "h00000fff".U) {
        regDone := true.B
        regFinalData := 0.U
        regAddr := 0.U
        regValid := false.B
      } .otherwise {
        regFinalData := data
        regAddr := regAddr + 1.U
        regValid := true.B
      }

    }
  }

  when(count === 4.U) {
    count := 0.U
  }

  io.addr_o := regAddr
  io.rx_data_o := regFinalData
  io.valid := regValid

  //  val mi = Module(new MasterInterface(sourceId = 3.U, forFetch = false))
  //  mi.io.memRd := 0.U
  //  mi.io.memWrt := 1.U
  //  mi.io.addr_in := regAddr
  //  mi.io.data_in := regFinalData
  //
  //  io.masterInterfaceIO.a_address := mi.io.a_address
  //  io.masterInterfaceIO.a_data := mi.io.a_data
  //  io.masterInterfaceIO.a_opcode := mi.io.a_opcode
  //  io.masterInterfaceIO.a_source := mi.io.a_source
  //  io.masterInterfaceIO.a_valid := mi.io.a_valid
  //
  //  mi.io.d_data := io.masterInterfaceIO.d_data
  //  mi.io.d_denied := io.masterInterfaceIO.d_denied
  //  mi.io.d_opcode := io.masterInterfaceIO.d_opcode
  //  mi.io.d_valid := io.masterInterfaceIO.d_valid
  //  mi.io.d_source := io.masterInterfaceIO.d_source




  //  io.addr := regAddr
  //  io.finalData := regFinalData
  //  io.en := regEn
  io.done := regDone

}