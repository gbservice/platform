package roman.actions;

import platform.base.BaseUtils;
import platform.base.IOUtils;
import platform.base.OrderedMap;
import platform.interop.Compare;
import platform.interop.action.ExportFileClientAction;
import platform.server.classes.ConcreteClass;
import platform.server.classes.StaticCustomClass;
import platform.server.classes.ValueClass;
import platform.server.data.expr.KeyExpr;
import platform.server.data.query.Query;
import platform.server.logics.DataObject;
import platform.server.logics.ObjectValue;
import platform.server.logics.linear.LCP;
import platform.server.logics.property.ClassPropertyInterface;
import platform.server.logics.property.ExecutionContext;
import platform.server.logics.scripted.ScriptingActionProperty;
import platform.server.logics.scripted.ScriptingErrorLog;
import platform.server.logics.scripted.ScriptingLogicsModule;
import platform.server.session.DataSession;

import java.io.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;

public class FiscalDatecsPrintReceiptActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface receiptInterface;

    public FiscalDatecsPrintReceiptActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, new ValueClass[]{LM.findClassByCompoundName("receipt")});

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        receiptInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) {

        DataSession session = context.getSession();
        DataObject receiptObject = context.getKeyValue(receiptInterface);

        try {
            Integer comPort = (Integer) LM.findLCPByCompoundName("comPortCurrentCashRegister").read(session);
            Integer baudRate = (Integer) LM.findLCPByCompoundName("baudRateCurrentCashRegister").read(session);
            Integer placeNumber = (Integer) LM.findLCPByCompoundName("nppMachineryCurrentCashRegister").read(session);
            ObjectValue userObject = LM.findLCPByCompoundName("userReceipt").readClasses(session, receiptObject);
            Object operatorNumber = userObject.isNull() ? 0 : LM.findLCPByCompoundName("operatorNumberCurrentCashRegister").read(session, (DataObject) userObject);
            Double sumTotal = (Double) LM.findLCPByCompoundName("sumReceiptDetailReceipt").read(session, receiptObject);
            Double sumDisc = (Double) LM.findLCPByCompoundName("discountSumReceiptDetailReceipt").read(session, receiptObject);
            Double sumCard = null;
            Double sumCash = null;

            KeyExpr paymentExpr = new KeyExpr("payment");
            Map<Object, KeyExpr> paymentKeys = new HashMap<Object, KeyExpr>();
            paymentKeys.put("payment", paymentExpr);

            Query<Object, Object> paymentQuery = new Query<Object, Object>(paymentKeys);
            paymentQuery.properties.put("sumPayment", getLCP("sumPayment").getExpr(context.getModifier(), paymentExpr));
            paymentQuery.properties.put("paymentMeansPayment", getLCP("paymentMeansPayment").getExpr(context.getModifier(), paymentExpr));

            paymentQuery.and(getLCP("receiptPayment").getExpr(context.getModifier(), paymentQuery.mapKeys.get("payment")).compare(receiptObject.getExpr(), Compare.EQUALS));

            OrderedMap<Map<Object, Object>, Map<Object, Object>> paymentResult = paymentQuery.execute(session.sql);
            for (Map<Object, Object> paymentValues : paymentResult.values()) {
                DataObject paymentMeansCashObject = ((StaticCustomClass) LM.findClassByCompoundName("paymentMeans")).getDataObject("paymentMeansCash");
                DataObject paymentMeansCardObject = ((StaticCustomClass) LM.findClassByCompoundName("paymentMeans")).getDataObject("paymentMeansCard");
                if (paymentMeansCashObject.getValue().equals(paymentValues.get("paymentMeansPayment"))) {
                    sumCash = (Double) paymentValues.get("sumPayment");
                } else if (paymentMeansCardObject.getValue().equals(paymentValues.get("paymentMeansPayment"))) {
                    sumCard = (Double) paymentValues.get("sumPayment");
                }
            }

            KeyExpr receiptDetailExpr = new KeyExpr("receiptDetail");
            Map<Object, KeyExpr> receiptDetailKeys = new HashMap<Object, KeyExpr>();
            receiptDetailKeys.put("receiptDetail", receiptDetailExpr);

            Query<Object, Object> receiptDetailQuery = new Query<Object, Object>(receiptDetailKeys);
            receiptDetailQuery.properties.put("nameSkuReceiptDetail", getLCP("nameSkuReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
            receiptDetailQuery.properties.put("quantityReceiptSaleDetail", getLCP("quantityReceiptSaleDetail").getExpr(context.getModifier(), receiptDetailExpr));
            receiptDetailQuery.properties.put("quantityReceiptReturnDetail", getLCP("quantityReceiptReturnDetail").getExpr(context.getModifier(), receiptDetailExpr));
            receiptDetailQuery.properties.put("priceReceiptDetail", getLCP("priceReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
            receiptDetailQuery.properties.put("idBarcodeReceiptDetail", getLCP("idBarcodeReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
            receiptDetailQuery.properties.put("sumReceiptDetail", getLCP("sumReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
            receiptDetailQuery.properties.put("discountPercentReceiptSaleDetail", getLCP("discountPercentReceiptSaleDetail").getExpr(context.getModifier(), receiptDetailExpr));
            receiptDetailQuery.properties.put("discountSumReceiptDetail", getLCP("discountSumReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
            receiptDetailQuery.properties.put("numberVATReceiptDetail", getLCP("numberVATReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));

            receiptDetailQuery.and(getLCP("receiptReceiptDetail").getExpr(context.getModifier(), receiptDetailQuery.mapKeys.get("receiptDetail")).compare(receiptObject.getExpr(), Compare.EQUALS));

            OrderedMap<Map<Object, Object>, Map<Object, Object>> receiptDetailResult = receiptDetailQuery.execute(session.sql);
            List<ReceiptItem> receiptSaleItemList = new ArrayList<ReceiptItem>();
            List<ReceiptItem> receiptReturnItemList = new ArrayList<ReceiptItem>();
            for (Map<Object, Object> receiptDetailValues : receiptDetailResult.values()) {
                Double price = (Double) receiptDetailValues.get("priceReceiptDetail");
                Double quantitySale = (Double) receiptDetailValues.get("quantityReceiptSaleDetail");
                Double quantityReturn = (Double) receiptDetailValues.get("quantityReceiptReturnDetail");
                String barcode = (String) receiptDetailValues.get("idBarcodeReceiptDetail");
                String name = (String) receiptDetailValues.get("nameSkuReceiptDetail");
                Double sumReceiptDetail = (Double) receiptDetailValues.get("sumReceiptDetail");
                Double discountPercentReceiptSaleDetail = (Double) receiptDetailValues.get("discountPercentReceiptSaleDetail");
                Double discountSumReceiptDetail = (Double) receiptDetailValues.get("discountSumReceiptDetail");
                Integer taxNumber = (Integer) receiptDetailValues.get("numberVATReceiptDetail");
                if (quantitySale != null)
                    receiptSaleItemList.add(new ReceiptItem(price, quantitySale, barcode, name.trim(), sumReceiptDetail,
                            discountPercentReceiptSaleDetail, discountSumReceiptDetail, taxNumber, 1));
                if (quantityReturn != null)
                    receiptReturnItemList.add(new ReceiptItem(price, quantityReturn, barcode, name.trim(), sumReceiptDetail,
                            discountPercentReceiptSaleDetail, discountSumReceiptDetail, taxNumber, 1));
            }

            if (context.checkApply(LM.getBL()))
                if (context.requestUserInteraction(new FiscalDatecsPrintReceiptClientAction(comPort, baudRate, placeNumber, operatorNumber == null ? 0 : (Integer) operatorNumber, new ReceiptInstance(sumDisc, sumCard, sumCash, sumTotal, receiptSaleItemList, receiptReturnItemList))) == null) {
                    context.apply(LM.getBL());
                    LM.findLAPByCompoundName("createCurrentReceipt").execute(session);
                }

        } catch (SQLException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }


    }
}
