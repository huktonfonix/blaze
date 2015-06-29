package org.apache.spark.acc_runtime

import java.io.OutputStream    
import java.io.FileOutputStream
import java.util.ArrayList     
import java.nio.ByteBuffer     
import java.nio.ByteOrder      

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

import org.apache.spark._
import org.apache.spark.{Partition, TaskContext}
import org.apache.spark.rdd._
import org.apache.spark.storage._
import org.apache.spark.scheduler._

class RDD_ACC[U:ClassTag, T: ClassTag](prev: RDD[T], f: T => U) 
  extends RDD[U](prev) {

  override def getPartitions: Array[Partition] = firstParent[T].partitions

  override def compute(split: Partition, context: TaskContext) = {
    val input_iter = firstParent[T].iterator(split, context)

    val output_iter = new Iterator[U] {
      val inputAry: Array[T] = input_iter.toArray
      val outputAry: Array[U] = new Array[U](inputAry.length)
      var idx: Int = 0

      // TODO: We should send either data (memory mapped file) or file path,
      // but now we just send data.
      val mappedFileInfo = Util.serializePartition(inputAry, split.index)
      println("Message info: " + mappedFileInfo._1 + ", " + mappedFileInfo._2 + ", " + mappedFileInfo._3)
      var msg: AccMessage.TaskMsg = 
        DataTransmitter.createTaskMsg(split.index, AccMessage.MsgType.ACCREQUEST)
      DataTransmitter.send(msg)
      msg = DataTransmitter.receive()

      // TODO: We should retry if rejected.
      if (msg.getType() != AccMessage.MsgType.ACCGRANT)
        throw new RuntimeException("Request reject.")
      else
        println("Acquire resource, sending data...")

      msg = DataTransmitter.createDataMsg(
          split.index, 
          mappedFileInfo._2, 
          mappedFileInfo._3,
          mappedFileInfo._1)

      DataTransmitter.send(msg)
      msg = DataTransmitter.receive()

      if (msg.getType() == AccMessage.MsgType.ACCFINISH) {
        // read result
        Util.readMemoryMappedFile(outputAry, mappedFileInfo._1 + ".out")
      }
      else {
        // in case of using CPU
        println("Compute partition " + split.index + " using CPU")
        for (e <- inputAry) {
          outputAry(idx) = f(e.asInstanceOf[T])
          idx = idx + 1
        }
        idx = 0
      }

      def hasNext(): Boolean = {
        idx < inputAry.length
      }

      def next(): U = {
        idx = idx + 1
        outputAry(idx - 1)
      }
    }
    output_iter
  }
}
