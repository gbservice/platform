package tmc.integration.exp.FiscalRegistar;

import java.io.IOException;

import com.jacob.com.Dispatch;
import platform.interop.action.AbstractClientAction;
import platform.interop.action.ClientActionDispatcher;


public class CashRegPrintReceiptAction extends AbstractClientAction {
    final static int FONT = 2;

    ReceiptInstance receipt;
    int type;
    int comPort;

    public CashRegPrintReceiptAction(int type, int comPort, ReceiptInstance receipt) {
        this.receipt = receipt;
        this.type = type;
        this.comPort = comPort;
    }

    @Override
    public void dispatch(ClientActionDispatcher dispatcher) throws IOException {
        Dispatch cashDispatch = FiscalReg.getDispatch(comPort);
        Dispatch.call(cashDispatch, "OpenFiscalDoc", type);

        try {
            //печать заголовка
            int k = FiscalReg.printHeaderAndNumbers(cashDispatch);

            //печать товаров
            for (ReceiptItem item : receipt.list) {
                Dispatch.call(cashDispatch, "AddCustom", item.barCode, FONT, 0, k++);
                String name = item.name.substring(0, Math.min(item.name.length(), FiscalReg.WIDTH));
                Dispatch.call(cashDispatch, "AddCustom", name, FONT, 0, k++);
                Dispatch.invoke(cashDispatch, "AddItem", Dispatch.Method, new Object[]{0, item.price, false,
                        0, 1, 0, item.quantity * 1000, 3, 0, "шт.", 0, 0, k++, 0}, new int[1]);
            }

            //Общая информация
            Dispatch.call(cashDispatch, "AddCustom", FiscalReg.delimetr, FONT, 0, k++);
            if (receipt.sumDisc > 0) {
                Dispatch.call(cashDispatch, "AddDocAmountAdj", -receipt.sumDisc, 0, FONT, 0, k++, 15);
            }
            Dispatch.call(cashDispatch, "AddTotal", FONT, 0, k++, 15);

            if (type == 1) {
                //выбор варианта оплаты
                boolean needChange = true;
                if (receipt.sumCash > 0 && receipt.sumCard > 0) {
                    Dispatch.call(cashDispatch, "AddPay", 4, receipt.sumCash, receipt.sumCard, "Pay", FONT, 0, k++, 15);
                } else if (receipt.sumCard > 0) {
                    Dispatch.call(cashDispatch, "AddPay", 2, receipt.sumCard, receipt.sumCard, "Pay", FONT, 0, k++, 25);
                    needChange = false;
                } else {
                    Dispatch.call(cashDispatch, "AddPay", 0, receipt.sumCash, receipt.sumCard, "Pay", FONT, 0, k++, 15);
                }

                if (needChange) {
                    Dispatch.call(cashDispatch, "AddChange", FONT, 0, k++, 15);
                }
            }
            Dispatch.call(cashDispatch, "CloseFiscalDoc");

        } catch (RuntimeException e) {
            Dispatch.call(cashDispatch, "CancelFiscalDoc", false);
            throw e;
        }
    }
}
