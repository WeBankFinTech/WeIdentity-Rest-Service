package com.webank.weid.http.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.weid.config.FiscoConfig;
import com.webank.weid.constant.ParamKeyConstant;
import com.webank.weid.constant.WeIdConstant;
import com.webank.weid.exception.WeIdBaseException;
import com.webank.weid.http.constant.HttpReturnCode;
import com.webank.weid.http.protocol.response.HttpResponseData;
import com.webank.weid.service.BaseService;
import com.webank.weid.util.DataToolUtils;
import com.webank.weid.util.DateUtils;
import com.webank.weid.util.WeIdUtils;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.apache.commons.lang3.StringUtils;
import org.bcos.web3j.utils.Numeric;
import org.fisco.bcos.web3j.abi.TypeReference;
import org.fisco.bcos.web3j.abi.datatypes.Address;
import org.fisco.bcos.web3j.abi.datatypes.DynamicBytes;
import org.fisco.bcos.web3j.abi.datatypes.Function;
import org.fisco.bcos.web3j.abi.datatypes.generated.Int256;
import org.fisco.bcos.web3j.crypto.ExtendedRawTransaction;
import org.fisco.bcos.web3j.crypto.Sign;
import org.fisco.bcos.web3j.crypto.Sign.SignatureData;
import org.fisco.bcos.web3j.protocol.Web3j;
import org.fisco.bcos.web3j.protocol.core.methods.response.NodeVersion;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;
import org.fisco.bcos.web3j.rlp.RlpType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionEncoderUtilV2 {

    private static Logger logger = LoggerFactory.getLogger(TransactionEncoderUtilV2.class);

    public static HttpResponseData<String> createWeIdEncoder(
        String inputParam,
        String nonce,
        String to) {
        Function function = buildCreateWeIdFunction(inputParam);
        if (function == null) {
            logger.error("[CreateWeId] Error occurred when building input param with: {}",
                inputParam);
            return new HttpResponseData<>(StringUtils.EMPTY, HttpReturnCode.INPUT_ILLEGAL);
        }
        FiscoConfig fiscoConfig = new FiscoConfig();
        fiscoConfig.load();
        String encodeResult = createClientEncodeResult(function, nonce, to, fiscoConfig.getGroupId());
        return new HttpResponseData<>(encodeResult, HttpReturnCode.SUCCESS);
    }

    public static String createTxnHex(String encodedSig, String nonce, String to, String data) {
        SignatureData sigData = TransactionEncoderUtilV2
            .simpleSignatureDeserialization(DataToolUtils.base64Decode(encodedSig.getBytes()));
        FiscoConfig fiscoConfig = new FiscoConfig();
        fiscoConfig.load();
        ExtendedRawTransaction rawTransaction = TransactionEncoderUtilV2.buildRawTransaction(nonce,
            fiscoConfig.getGroupId(), data, to);
        byte[] encodedSignedTxn = TransactionEncoderUtilV2.encode(rawTransaction, sigData);
        return Numeric.toHexString(encodedSignedTxn);
    }

    public static String createClientEncodeResult(Function function, String nonce, String to, String groupId) {
        // 1. encode the Function
        String data = org.fisco.bcos.web3j.abi.FunctionEncoder.encode(function);
        // 2. server generate encodedTransaction
        Web3j web3j = (Web3j) BaseService.getWeb3j();
        ExtendedRawTransaction rawTransaction = TransactionEncoderUtilV2.buildRawTransaction(nonce,
            groupId, data, to);
        byte[] encodedTransaction = TransactionEncoderUtilV2.encode(rawTransaction);
        // 3. server sends encodeTransaction (in base64) and data back to client
        return TransactionEncoderUtil.getEncodeOutput(encodedTransaction, data);
    }

    public static Function buildCreateWeIdFunction(String inputParam) {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode inputParamNode;
        try {
            inputParamNode = objectMapper.readTree(inputParam);
        } catch (Exception e) {
            logger.error("Failed to decode JsonInput");
            return null;
        }
        JsonNode publicKeyNode = inputParamNode.get(ParamKeyConstant.PUBLIC_KEY);
        if (publicKeyNode == null) {
            return null;
        }
        String publicKey = publicKeyNode.textValue();
        if (StringUtils.isEmpty(publicKey)) {
            logger.error("[createWeId]: input parameter publickey is null.");
            return null;
        }
        String weId = WeIdUtils.convertPublicKeyToWeId(publicKey);
        String addr = WeIdUtils.convertWeIdToAddress(weId);
        if (!WeIdUtils.isValidAddress(addr)) {
            logger.error("[createWeId]: input parameter publickey is invalid.");
            return null;
        }
        String auth = new StringBuffer()
            .append(publicKey)
            .append(WeIdConstant.SEPARATOR)
            .append(addr)
            .toString();
        return new Function(
            "createWeId",
            Arrays.<org.fisco.bcos.web3j.abi.datatypes.Type>asList(
                new Address(addr),
                new DynamicBytes(DataToolUtils.stringToByteArray(auth)),
                new DynamicBytes(DataToolUtils.stringToByteArray(DateUtils.getNoMillisecondTimeStampString())),
                new Int256(BigInteger.valueOf(DateUtils.getNoMillisecondTimeStamp()))
            ),
            Collections.<TypeReference<?>>emptyList());
    }

    public static BigInteger getNonce() {
        Random r = new SecureRandom();
        BigInteger randomid = new BigInteger(250, r);
        return randomid;
    }

    public static byte[] encode(ExtendedRawTransaction rawTransaction) {
        return encode(rawTransaction, null);
    }

    public static byte[] encode(
        ExtendedRawTransaction rawTransaction, Sign.SignatureData signatureData) {
        List<RlpType> values = asRlpValues(rawTransaction, signatureData);
        org.fisco.bcos.web3j.rlp.RlpList rlpList = new org.fisco.bcos.web3j.rlp.RlpList(values);
        return org.fisco.bcos.web3j.rlp.RlpEncoder.encode(rlpList);
    }

    static List<org.fisco.bcos.web3j.rlp.RlpType> asRlpValues(
        ExtendedRawTransaction rawTransaction, Sign.SignatureData signatureData) {
        List<org.fisco.bcos.web3j.rlp.RlpType> result = new ArrayList<>();
        result.add(org.fisco.bcos.web3j.rlp.RlpString.create(rawTransaction.getRandomid()));
        result.add(org.fisco.bcos.web3j.rlp.RlpString.create(rawTransaction.getGasPrice()));
        result.add(org.fisco.bcos.web3j.rlp.RlpString.create(rawTransaction.getGasLimit()));
        result.add(org.fisco.bcos.web3j.rlp.RlpString.create(rawTransaction.getBlockLimit()));
        // an empty to address (contract creation) should not be encoded as a numeric 0 value
        String to = rawTransaction.getTo();
        if (to != null && to.length() > 0) {
            // addresses that start with zeros should be encoded with the zeros included, not
            // as numeric values
            result.add(org.fisco.bcos.web3j.rlp.RlpString.create(org.fisco.bcos.web3j.utils.Numeric.hexStringToByteArray(to)));
        } else {
            result.add(org.fisco.bcos.web3j.rlp.RlpString.create(""));
        }

        result.add(org.fisco.bcos.web3j.rlp.RlpString.create(rawTransaction.getValue()));

        // value field will already be hex encoded, so we need to convert into binary first
        byte[] data = org.fisco.bcos.web3j.utils.Numeric.hexStringToByteArray(rawTransaction.getData());
        result.add(org.fisco.bcos.web3j.rlp.RlpString.create(data));

        // add extra data!!!

        result.add(org.fisco.bcos.web3j.rlp.RlpString.create(rawTransaction.getFiscoChainId()));
        result.add(org.fisco.bcos.web3j.rlp.RlpString.create(rawTransaction.getGroupId()));
        if (rawTransaction.getExtraData() == null) {
            result.add(org.fisco.bcos.web3j.rlp.RlpString.create(""));
        } else {
            result.add(
                org.fisco.bcos.web3j.rlp.RlpString.create(org.fisco.bcos.web3j.utils.Numeric.hexStringToByteArray(rawTransaction.getExtraData())));
        }
        if (signatureData != null) {
            if (org.fisco.bcos.web3j.crypto.EncryptType.encryptType == 1) {
                result.add(org.fisco.bcos.web3j.rlp.RlpString.create(org.fisco.bcos.web3j.utils.Bytes.trimLeadingZeroes(signatureData.getPub())));
                // logger.debug("RLP-Pub:{},RLP-PubLen:{}",Hex.toHexString(signatureData.getPub()),signatureData.getPub().length);
                result.add(org.fisco.bcos.web3j.rlp.RlpString.create(org.fisco.bcos.web3j.utils.Bytes.trimLeadingZeroes(signatureData.getR())));
                // logger.debug("RLP-R:{},RLP-RLen:{}",Hex.toHexString(signatureData.getR()),signatureData.getR().length);
                result.add(org.fisco.bcos.web3j.rlp.RlpString.create(org.fisco.bcos.web3j.utils.Bytes.trimLeadingZeroes(signatureData.getS())));
                // logger.debug("RLP-S:{},RLP-SLen:{}",Hex.toHexString(signatureData.getS()),signatureData.getS().length);
            } else {
                result.add(org.fisco.bcos.web3j.rlp.RlpString.create(signatureData.getV()));
                result.add(org.fisco.bcos.web3j.rlp.RlpString.create(org.fisco.bcos.web3j.utils.Bytes.trimLeadingZeroes(signatureData.getR())));
                result.add(org.fisco.bcos.web3j.rlp.RlpString.create(org.fisco.bcos.web3j.utils.Bytes.trimLeadingZeroes(signatureData.getS())));
            }
        }
        return result;
    }

    public static ExtendedRawTransaction buildRawTransaction(String nonce, String groupId, String data, String to) {
        ExtendedRawTransaction rawTransaction =
            ExtendedRawTransaction.createTransaction(
                new BigInteger(nonce),
                new BigInteger("99999999999"),
                new BigInteger("99999999999"),
                getBlocklimitV2(),
                to, // to address
                BigInteger.ZERO, // value to transfer
                data,
                getChainIdV2(), // chainId
                new BigInteger(groupId), // groupId
                null);
        return rawTransaction;
    }

    public static byte[] simpleSignatureSerialization(Sign.SignatureData signatureData) {
        byte[] serializedSignatureData = new byte[65];
        serializedSignatureData[0] = signatureData.getV();
        System.arraycopy(signatureData.getR(), 0, serializedSignatureData, 1, 32);
        System.arraycopy(signatureData.getS(), 0, serializedSignatureData, 33, 32);
        return serializedSignatureData;
    }

    public static Sign.SignatureData simpleSignatureDeserialization(
        byte[] serializedSignatureData) {
        if (65 != serializedSignatureData.length) {
            throw new WeIdBaseException("signature data illegal");
        }
        byte v = serializedSignatureData[0];
        byte[] r = new byte[32];
        byte[] s = new byte[32];
        System.arraycopy(serializedSignatureData, 1, r, 0, 32);
        System.arraycopy(serializedSignatureData, 33, s, 0, 32);
        Sign.SignatureData signatureData = new Sign.SignatureData(v, r, s);
        return signatureData;
    }

    /**
     * Get the chainId for FISCO-BCOS v2.x chainId. Consumed by Restful API service.
     *
     * @return chainId in BigInt.
     */
    public static BigInteger getChainIdV2() {
        try {
            NodeVersion.Version nodeVersion = ((org.fisco.bcos.web3j.protocol.Web3j) BaseService
                .getWeb3j()).getNodeVersion().send().getNodeVersion();
            String chainId = nodeVersion.getChainID();
            return new BigInteger(chainId);
        } catch (Exception e) {
            return BigInteger.ONE;
        }
    }

    /**
     * Get the Blocklimit for FISCO-BCOS v2.0 blockchain. This already adds 600 to block height.
     *
     * @return chainId in BigInt.
     */
    public static BigInteger getBlocklimitV2() {
        try {
            return ((org.fisco.bcos.web3j.protocol.Web3j) BaseService.getWeb3j())
                .getBlockNumberCache();
        } catch (Exception e) {
            return null;
        }
    }

    public static Optional<TransactionReceipt> getTransactionReceiptRequest(String transactionHash) {
        Optional<TransactionReceipt> receiptOptional = Optional.empty();
        Web3j web3j = (Web3j) BaseService.getWeb3j();
        try {
            for (int i = 0; i < 5; i++) {
                receiptOptional = web3j.getTransactionReceipt(transactionHash).send().getTransactionReceipt();
                if (!receiptOptional.isPresent()) {
                    Thread.sleep(1000);
                } else {
                    return receiptOptional;
                }
            }
        } catch (IOException | InterruptedException e) {
            System.out.println();
        }
        return receiptOptional;
    }
}