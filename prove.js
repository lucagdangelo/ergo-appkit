
const ErgoUnsafeProver = Java.type("org.ergoplatform.wallet.interface4j.crypto.ErgoUnsafeProver")
const UnsignedErgoLikeTransaction = Java.type("org.ergoplatform.UnsignedErgoLikeTransaction")
const ErgoBoxBuilder = Java.type("org.ergoplatform.polyglot.ErgoBoxBuilder")
const ErgoTransactionBuilder = Java.type("org.ergoplatform.polyglot.ErgoTransactionBuilder")
const ErgoProverBuilder = Java.type("org.ergoplatform.polyglot.ErgoProverBuilder")

function printMethods(obj) {
    print("--------------------------")
    print("Methods of " + obj)
    for (var m in obj) { print(m); }
}


printMethods(ErgoUnsafeProver)
printMethods(UnsignedErgoLikeTransaction)

let unsafeProver = new ErgoUnsafeProver
printMethods(unsafeProver)

let txB = new ErgoTransactionBuilder
printMethods(txB)

let boxB = new ErgoBoxBuilder
printMethods(txB)

let box = boxB.withValue(10).build()

let tx = txB
    .withInputs("bb3c8c41611a9e6d469ebbf8f13de43666b2a92d03e14895e066a21fe62910d7")
    .withCandidates(box)
    .build();
printMethods(tx)

let proverB = new ErgoProverBuilder
printMethods(proverB)

let prover = proverB.withSeed("abc").build()
printMethods(prover)

let signed = prover.sign(tx)
printMethods(signed)