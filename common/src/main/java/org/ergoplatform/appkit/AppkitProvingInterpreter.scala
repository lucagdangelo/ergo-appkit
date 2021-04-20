package org.ergoplatform.appkit

import org.ergoplatform.validation.ValidationRules
import org.ergoplatform.wallet.interpreter.ErgoInterpreter
import sigmastate.basics.DLogProtocol.{ProveDlog, DLogProverInput}
import java.util
import java.util.{List => JList}

import org.ergoplatform.wallet.secrets.ExtendedSecretKey
import sigmastate.basics.{SigmaProtocol, SigmaProtocolPrivateInput, SigmaProtocolCommonInput, DiffieHellmanTupleProverInput}
import org.ergoplatform._
import org.ergoplatform.utils.ArithUtils
import org.ergoplatform.wallet.protocol.context.{ErgoLikeParameters, ErgoLikeStateContext, TransactionContext}

import scala.util.Try
import sigmastate.eval.CompiletimeIRContext
import sigmastate.interpreter.ProverInterpreter
import sigmastate.utxo.CostTable
import sigmastate.utils.Helpers._  // don't remove, required for Scala 2.11
import scala.collection.mutable

object Helpers {
  implicit class AppkitTryOps[A](val source: Try[A]) extends AnyVal {
    def mapOrThrow[B](f: A => B): B = source.fold(t => throw t, f)
  }
}

/**
 * A class which holds secrets and can sign transactions (aka generate proofs).
 *
 * @param secretKeys secrets in extended form to be used by prover
 * @param dhtInputs  prover inputs containing secrets for generating proofs for ProveDHTuple nodes.
 * @param params     ergo blockchain parameters
 */
class AppkitProvingInterpreter(
      val secretKeys: JList[ExtendedSecretKey],
      val dLogInputs: JList[DLogProverInput],
      val dhtInputs: JList[DiffieHellmanTupleProverInput],
      params: ErgoLikeParameters)
  extends ErgoLikeInterpreter()(new CompiletimeIRContext) with ProverInterpreter {

  override type CTX = ErgoLikeContext
  import Iso._
  import Helpers._

  val secrets: Seq[SigmaProtocolPrivateInput[_ <: SigmaProtocol[_], _ <: SigmaProtocolCommonInput[_]]] = {
    val dlogs: IndexedSeq[DLogProverInput] = JListToIndexedSeq(identityIso[ExtendedSecretKey]).to(secretKeys).map(_.privateInput)
    val dlogsAdditional: IndexedSeq[DLogProverInput] = JListToIndexedSeq(identityIso[DLogProverInput]).to(dLogInputs)
    val dhts: IndexedSeq[DiffieHellmanTupleProverInput] = JListToIndexedSeq(identityIso[DiffieHellmanTupleProverInput]).to(dhtInputs)
    dlogs ++ dlogsAdditional ++ dhts
  }

  val pubKeys: Seq[ProveDlog] = secrets
    .filter { case _: DLogProverInput => true case _ => false}
    .map(_.asInstanceOf[DLogProverInput].publicImage)

  def addCost(currentCost: Long, delta: Long, limit: Long, msg: String): Long = {
    val newCost = Math.addExact(currentCost, delta)
    if (newCost > limit)
      throw new Exception(s"Cost of transaction $newCost exceeds limit $limit: $msg")
    newCost
  }

  /**
   * @note requires `unsignedTx` and `boxesToSpend` have the same boxIds in the same order.
   */
  def sign(unsignedTx: UnsignedErgoLikeTransaction,
           boxesToSpend: IndexedSeq[ExtendedInputBox],
           dataBoxes: IndexedSeq[ErgoBox],
           stateContext: ErgoLikeStateContext,
           baseCost: Int): Try[ErgoLikeTransaction] = Try {
    if (unsignedTx.inputs.length != boxesToSpend.length) throw new Exception("Not enough boxes to spend")
    if (unsignedTx.dataInputs.length != dataBoxes.length) throw new Exception("Not enough data boxes")

    // Cost of transaction initialization: we should read and parse all inputs and data inputs,
    // and also iterate through all outputs to check rules
    val initialCost = ArithUtils.addExact(
      CostTable.interpreterInitCost,
      Math.multiplyExact(boxesToSpend.size, params.inputCost),
      Math.multiplyExact(dataBoxes.size, params.dataInputCost),
      Math.multiplyExact(unsignedTx.outputCandidates.size, params.outputCost)
    )
    val maxCost = params.maxBlockCost
    val startCost = addCost(baseCost, initialCost, maxCost, msg = unsignedTx.toString())

    val transactionContext = TransactionContext(boxesToSpend.map(_.box), dataBoxes, unsignedTx)

    val provedInputs = mutable.ArrayBuilder.make[Input]()
    var currentCost = startCost
    for ((inputBox, boxIdx) <- boxesToSpend.zipWithIndex) {
      val unsignedInput = unsignedTx.inputs(boxIdx)
      require(util.Arrays.equals(unsignedInput.boxId, inputBox.box.id))

      val context = new ErgoLikeContext(
        ErgoInterpreter.avlTreeFromDigest(stateContext.previousStateDigest),
        stateContext.sigmaLastHeaders,
        stateContext.sigmaPreHeader,
        transactionContext.dataBoxes,
        transactionContext.boxesToSpend,
        transactionContext.spendingTransaction,
        boxIdx.toShort,
        inputBox.extension,
        ValidationRules.currentSettings,
        costLimit = maxCost - currentCost,
        initCost = 0,
        activatedScriptVersion = (params.blockVersion - 1).toByte
      )

      val proverResult = prove(inputBox.box.ergoTree, context, unsignedTx.messageToSign).getOrThrow
      val signedInput = Input(unsignedInput.boxId, proverResult)

      currentCost = addCost(currentCost, proverResult.cost, maxCost, msg = signedInput.toString())

      provedInputs += signedInput
    }

    new ErgoLikeTransaction(provedInputs.result(), unsignedTx.dataInputs, unsignedTx.outputCandidates)
  }

}
