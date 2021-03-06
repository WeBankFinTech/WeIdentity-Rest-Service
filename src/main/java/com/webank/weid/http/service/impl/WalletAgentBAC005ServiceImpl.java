package com.webank.weid.http.service.impl;

import com.webank.payment.protocol.base.BaseAsset;
import com.webank.weid.exception.WeIdBaseException;
import com.webank.weid.http.constant.HttpReturnCode;
import com.webank.weid.http.constant.WalletAgentFunctionNames;
import com.webank.weid.http.protocol.request.InputArg;
import com.webank.weid.http.protocol.request.ReqInput;
import com.webank.weid.http.protocol.request.payment.*;
import com.webank.weid.http.protocol.response.HttpResponseData;
import com.webank.weid.http.service.InvokerBAC005AssetService;
import com.webank.weid.http.service.WalletAgentBAC005Service;
import com.webank.weid.http.util.TransactionEncoderUtilV2;
import com.webank.weid.util.DataToolUtils;

import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WalletAgentBAC005ServiceImpl
    extends AbstractRawTransactionService
    implements WalletAgentBAC005Service {
    
    private Logger logger = LoggerFactory.getLogger(WalletAgentBAC005ServiceImpl.class);
    
    @Autowired
    private InvokerBAC005AssetService bac005AssetService;

    @Override
    public HttpResponseData<Object> invokeFunction(String invokeFunctionJsonArgs) {
        logger.info("inputArg:{}", invokeFunctionJsonArgs);

        HttpResponseData<InputArg> resp = TransactionEncoderUtilV2
                .buildInputArg(invokeFunctionJsonArgs);
        InputArg inputArg = resp.getRespBody();
        if (inputArg == null) {
            logger.error("Failed to build input argument: {}", invokeFunctionJsonArgs);
            return new HttpResponseData<>(null, resp.getErrorCode(), resp.getErrorMessage());
        }
        HttpResponseData<Object> responseData = this.invoke(inputArg);
        responseData.setLoopback(getLoopBack(inputArg.getTransactionArg()));
        return responseData;
    }

    private HttpResponseData<Object> invoke(InputArg inputArg) {
        String functionName = inputArg.getFunctionName();
        try {
            switch(functionName) {
            case WalletAgentFunctionNames.FUNCNAME_WALLETAGENT_CONSTRUCT:
                return bac005AssetService.construct(toReqInput(inputArg, BAC005Info.class));
            case WalletAgentFunctionNames.FUNCNAME_WALLETAGENT_ISSUE:
                return bac005AssetService.issue(toReqInput(inputArg, BAC005Info.class));
            case WalletAgentFunctionNames.FUNCNAME_WALLETAGENT_CONSTRUCTANDISSUE:
                return bac005AssetService.constructAndIssue(toReqInput(inputArg, BAC005Info.class));
            case WalletAgentFunctionNames.FUNCNAME_WALLETAGENT_BATCHISSUE:
                return bac005AssetService.batchIssue(toReqInput(inputArg, BAC005BatchInfo.class));
            case WalletAgentFunctionNames.FUNCNAME_WALLETAGENT_CONSTRUCTANDBATCHISSUE:
                return bac005AssetService.constructAndBatchIssue(toReqInput(inputArg, BAC005BatchInfo.class));
            case WalletAgentFunctionNames.FUNCNAME_WALLETAGENT_QUERYASSETOWNER:
                return bac005AssetService.queryAssetOwner(toReqInput(inputArg, BAC005Info.class));
            case WalletAgentFunctionNames.FUNCNAME_WALLETAGENT_QUERYASSETNUM:
                return bac005AssetService.queryAssetNum(toReqInput(inputArg, BaseAsset.class));
            case WalletAgentFunctionNames.FUNCNAME_WALLETAGENT_QUERYASSETLIST:
                return bac005AssetService.queryAssetList(toReqInput(inputArg, PageQuery.class));
            case WalletAgentFunctionNames.FUNCNAME_WALLETAGENT_QUERYOWNEDASSETNUM:
                return bac005AssetService.queryOwnedAssetNum(toReqInput(inputArg, BaseQuery.class));
            case WalletAgentFunctionNames.FUNCNAME_WALLETAGENT_QUERYOWNEDASSETLIST:
                return bac005AssetService.queryOwnedAssetList(toReqInput(inputArg, PageQuery.class));
            case WalletAgentFunctionNames.FUNCNAME_WALLETAGENT_SEND:
                return bac005AssetService.send(toReqInput(inputArg, BAC005Info.class));
            case WalletAgentFunctionNames.FUNCNAME_WALLETAGENT_BATCHSEND:
                return bac005AssetService.batchSend(toReqInput(inputArg, BAC005BatchInfo.class));
            case WalletAgentFunctionNames.FUNCNAME_WALLETAGENT_GETBASEINFO:
                return bac005AssetService.queryBaseInfo(toReqInput(inputArg, AssetAddressList.class));
            case WalletAgentFunctionNames.FUNCNAME_WALLETAGENT_GETBASEINFOBYWEID:
                return bac005AssetService.queryBaseInfoByWeId(toReqInput(inputArg, PageQuery.class));
            }
            logger.error("Function name undefined: {}.", functionName);
            return new HttpResponseData<>(null, HttpReturnCode.FUNCTION_NAME_ILLEGAL);
        } catch (WeIdBaseException e) {
            logger.error("[invokeFunction]: invoke {} failed, input argument {}",
                    functionName,
                    inputArg,
                    e);
            return new HttpResponseData<>(null, e.getErrorCode().getCode(), e.getMessage());
        } catch (Exception e) {
            logger.error("[invokeFunction]: unknown error with input argument {}",
                inputArg,
                e);
            return new HttpResponseData<>(null, HttpReturnCode.UNKNOWN_ERROR.getCode(),
                HttpReturnCode.UNKNOWN_ERROR.getCodeDesc());
        }
    }

    @Override
    protected HttpResponseData<String> doEncodeTransaction(InputArg inputArg) {
        String functionName = inputArg.getFunctionName();
        switch(functionName) {
        case WalletAgentFunctionNames.FUNCNAME_WALLETAGENT_CONSTRUCT:
            return bac005AssetService.constructEncoder(toReqInput(inputArg, BAC005Info.class));
        case WalletAgentFunctionNames.FUNCNAME_WALLETAGENT_ISSUE:
            return bac005AssetService.issueEncoder(toReqInput(inputArg, BAC005Info.class)); 
        case WalletAgentFunctionNames.FUNCNAME_WALLETAGENT_BATCHISSUE:
            return bac005AssetService.batchIssueEncoder(toReqInput(inputArg, BAC005BatchInfo.class));
        case WalletAgentFunctionNames.FUNCNAME_WALLETAGENT_SEND:
            return bac005AssetService.sendEncoder(toReqInput(inputArg, BAC005Info.class));
        case WalletAgentFunctionNames.FUNCNAME_WALLETAGENT_BATCHSEND:
            return bac005AssetService.batchSendEncoder(toReqInput(inputArg, BAC005BatchInfo.class));
        default:
            logger.error("Function name undefined: {}.", functionName);
            return new HttpResponseData<>(null, HttpReturnCode.FUNCTION_NAME_ILLEGAL);
        }
    }

    @Override
    protected HttpResponseData<Object> doSendTransaction(InputArg inputArg, TransactionReceipt receipt) {
        String functionName =  inputArg.getFunctionName();
        switch(functionName) {
        case WalletAgentFunctionNames.FUNCNAME_WALLETAGENT_CONSTRUCT:
            return bac005AssetService.constructDeCoder(receipt);
        case WalletAgentFunctionNames.FUNCNAME_WALLETAGENT_ISSUE:
            return bac005AssetService.issueDeCoder(receipt);
        case WalletAgentFunctionNames.FUNCNAME_WALLETAGENT_BATCHISSUE:
            return bac005AssetService.batchIssueDeCoder(receipt);
        case WalletAgentFunctionNames.FUNCNAME_WALLETAGENT_SEND:
            return bac005AssetService.sendDecoder(receipt);
        case WalletAgentFunctionNames.FUNCNAME_WALLETAGENT_BATCHSEND:
            return bac005AssetService.batchSendDecoder(receipt);
        default:
            logger.error("Function name undefined: {}.", functionName);
            return new HttpResponseData<>(null, HttpReturnCode.FUNCTION_NAME_ILLEGAL);
        }
    }

    @Override
    protected String doGetTo(ReqInput<String> req) {
        BaseAsset asset = DataToolUtils.deserialize(req.getFunctionArg(), BaseAsset.class);
        return asset.getAssetAddress();
    }
}
