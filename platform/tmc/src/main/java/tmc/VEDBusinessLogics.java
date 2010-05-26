package tmc;

import net.sf.jasperreports.engine.JRException;

import java.sql.SQLException;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.awt.event.KeyEvent;

import platform.server.data.sql.DataAdapter;
import platform.server.data.Union;
import platform.server.data.Time;
import platform.server.logics.BusinessLogics;
import platform.server.logics.linear.LP;
import platform.server.logics.property.AggregateProperty;
import platform.server.logics.property.ClassPropertyInterface;
import platform.server.logics.property.group.AbstractGroup;
import platform.server.classes.*;
import platform.server.view.navigator.*;
import platform.server.view.navigator.filter.*;
import platform.server.view.form.client.DefaultFormView;
import platform.server.auth.User;
import platform.interop.Compare;
import platform.interop.ClassViewType;
import platform.interop.form.layout.DoNotIntersectSimplexConstraint;
import platform.interop.form.layout.SimplexComponentDirections;

import javax.swing.*;
import java.awt.*;


public class VEDBusinessLogics extends BusinessLogics<VEDBusinessLogics> {

    public VEDBusinessLogics(DataAdapter adapter, int exportPort) throws IOException, ClassNotFoundException, SQLException, IllegalAccessException, InstantiationException, FileNotFoundException, JRException {
        super(adapter, exportPort);
    }

    // конкретные классы
    // реализация по безналу (в опт)
    CustomClass orderSaleWhole;
    CustomClass invoiceSaleWhole;
    CustomClass commitSaleWhole;

    // реализация в розницу
    CustomClass orderSaleRetail;
    CustomClass invoiceSaleRetail;
    CustomClass commitSaleInvoiceRetail;
    CustomClass commitSaleCheckRetail;
    // инвентаризация
    CustomClass balanceCheck;
    // закупка у местного поставщика
    CustomClass orderDeliveryLocal;
    CustomClass commitDeliveryLocal;
    CustomClass orderDeliveryShopLocal;
    CustomClass orderDeliveryWarehouseLocal;
    // закупка у импортного поставщика
    CustomClass orderDeliveryImport;

    // внутреннее перемещение
    CustomClass orderDistributeShop;
    CustomClass orderDistributeWarehouse;
    // возвраты
    // возврат местному поставщику
    CustomClass orderReturnDeliveryLocal, invoiceReturnDeliveryLocal, commitReturnDeliveryLocal;
    // возврат реализации по безналу
    CustomClass returnSaleWhole;
    // возврат реализации за наличный расчет
    CustomClass returnSaleInvoiceRetail;
    CustomClass returnSaleCheckRetail;

    CustomClass order, orderInc, orderOut, orderStoreOut;
    CustomClass invoiceDocument;
    CustomClass commitOut, commitInc;
    CustomClass orderSale;

    CustomClass documentInner;
    CustomClass orderDelivery;
    CustomClass commitDelivery;

    CustomClass move, moveInner, returnInner, returnOuter, orderInner;

    CustomClass supplier;
    CustomClass store, article, articleGroup, localSupplier, importSupplier, orderLocal, format;
    CustomClass customerWhole, customerRetail, orderWhole, orderRetail;

    CustomClass documentRevalue;
    CustomClass commitWholeShopInc;
    CustomClass documentNDS;
    CustomClass subject;

    CustomClass shop, warehouse;

    CustomClass orderShopInc, orderShopOut;
    CustomClass orderWarehouseInc, orderWarehouseOut;

    CustomClass action;
    CustomClass saleAction;
    CustomClass articleAction;
    CustomClass groupArticleAction;

    CustomClass obligation;
    CustomClass coupon;
    CustomClass giftObligation;

    protected void initClasses() {

        subject = addAbstractClass("Субъект", namedObject);

        action = addAbstractClass("Акция", baseClass);
        saleAction = addConcreteClass("Распродажа", action);
        articleAction = addConcreteClass("Акции по позициям", action);

        groupArticleAction = addConcreteClass("Группа акций", namedObject);

        store = addAbstractClass("Склад", subject);
        shop = addConcreteClass("Магазин", store);
        warehouse = addConcreteClass("Распред. центр", store);
        article = addConcreteClass("Товар", namedObject, barcodeObject);
        articleGroup = addConcreteClass("Группа товаров", namedObject);
        supplier = addAbstractClass("Поставщик", subject);
        localSupplier = addConcreteClass("Местный поставщик", supplier);
        importSupplier = addConcreteClass("Импортный поставщик", supplier);
        customerWhole = addConcreteClass("Оптовый покупатель", namedObject);
        customerRetail = addConcreteClass("Розничный покупатель", namedObject, barcodeObject);

        format = addConcreteClass("Формат", namedObject);

        documentShopPrice = addAbstractClass("Изменение цены в магазине", transaction);
        documentRevalue = addConcreteClass("Переоценка в магазине", documentShopPrice);

        documentNDS = addConcreteClass("Изменение НДС", transaction);

        // заявки на приход, расход
        order = addAbstractClass("Заявка", transaction);

        orderInc = addAbstractClass("Заявка прихода на склад", order);
        orderShopInc = addAbstractClass("Заявка прихода на магазин", orderInc);
        orderWarehouseInc = addAbstractClass("Заявка прихода на распред. центр", orderInc);

        documentInner = addAbstractClass("Внутренняя операция", order);
        returnInner = addAbstractClass("Возврат внутренней операции", order);
        orderInner = addAbstractClass("Заказ", documentInner);

        orderOut = addAbstractClass("Заявка расхода со склада", documentInner);
        orderStoreOut = addAbstractClass("Заявка расхода со склада", orderOut);
        orderShopOut = addAbstractClass("Заявка расхода с магазина", orderOut);
        orderWarehouseOut = addAbstractClass("Заявка расхода с распред. центра", orderOut);

        invoiceDocument = addAbstractClass("Заявка на перевозку", order);
        commitOut = addAbstractClass("Отгруженная заявка", order);
        commitInc = addAbstractClass("Принятая заявка", commitOut);

        commitWholeShopInc = addAbstractClass("Принятый оптовый приход на магазин", documentShopPrice, orderShopInc, commitInc);

        // внутр. и внешние операции
        orderDelivery = addAbstractClass("Закупка", orderInc); // всегда прих., создает партию - элементарную единицу учета
        commitDelivery = addAbstractClass("Приход от пост.", orderDelivery, commitInc, invoiceDocument);

        orderSale = addAbstractClass("Продажа", orderOut);

        orderWhole = addAbstractClass("Оптовая операция", order);
        orderRetail = addAbstractClass("Розничная операция", order);

        orderSaleWhole = addConcreteClass("Оптовый заказ", orderWarehouseOut, orderInner, orderWhole, orderSale);
        invoiceSaleWhole = addConcreteClass("Выписанный оптовый заказ", orderSaleWhole, invoiceDocument);
        commitSaleWhole = addConcreteClass("Отгруженный оптовый заказ", invoiceSaleWhole, commitOut);

        orderSaleRetail = addConcreteClass("Розничный заказ", orderShopOut, orderInner, orderRetail, orderSale);
        invoiceSaleRetail = addConcreteClass("Выписанная реализация по накладной", orderSaleRetail, invoiceDocument);
        commitSaleInvoiceRetail = addConcreteClass("Отгруженная реализация по накладной", invoiceSaleRetail, commitOut);
        commitSaleCheckRetail = addConcreteClass("Реализация через кассу", orderSaleRetail, commitOut);

        balanceCheck = addConcreteClass("Инвентаризация", orderStoreOut, commitOut, documentInner);

        orderDistributeShop = addConcreteClass("Заказ на внутреннее перемещение на магазин", orderWarehouseOut, orderShopInc, orderInner);
        addConcreteClass("Принятое внутреннее перемещение на магазин", commitWholeShopInc,
                addConcreteClass("Отгруженное внутреннее перемещение на магазин", commitOut,
                        addConcreteClass("Выписанное внутреннее перемещение на магазин", orderDistributeShop, invoiceDocument)));
        orderDistributeWarehouse = addConcreteClass("Заказ на внутреннее перемещение на распред. центр", orderStoreOut, orderWarehouseInc, orderInner);
        addConcreteClass("Принятое внутреннее перемещение на распред. центр", commitInc,
                addConcreteClass("Отгруженное внутреннее перемещение на распред. центр", commitOut,
                        addConcreteClass("Выписанное внутреннее перемещение на распред. центр", orderDistributeWarehouse, invoiceDocument)));

        orderLocal = addAbstractClass("Операция с местным поставщиком", order);

        orderDeliveryLocal = addAbstractClass("Закупка у местного поставщика", orderDelivery, orderLocal);
        commitDeliveryLocal = addAbstractClass("Приход от местного поставщика", orderDeliveryLocal, commitDelivery);

        orderDeliveryShopLocal = addConcreteClass("Закупка у местного поставщика на магазин", orderDeliveryLocal, orderShopInc);
        addConcreteClass("Приход от местного поставщика на магазин", orderDeliveryShopLocal, commitDeliveryLocal, commitWholeShopInc);

        orderDeliveryWarehouseLocal = addConcreteClass("Закупка у местного поставщика на распред. центр", orderDeliveryLocal, orderWarehouseInc);
        addConcreteClass("Приход от местного поставщика на распред. центр", orderDeliveryWarehouseLocal, commitDeliveryLocal);

        orderDeliveryImport = addConcreteClass("Закупка у импортного поставщика", orderDelivery, orderWarehouseInc);
        addConcreteClass("Приход от импортного поставщика", orderDeliveryImport, commitDelivery);

        orderReturnDeliveryLocal = addConcreteClass("Заявка на возврат местному поставщику", orderStoreOut, documentInner, orderLocal);
        invoiceReturnDeliveryLocal = addConcreteClass("Выписанная заявка на возврат местному поставщику", orderReturnDeliveryLocal,invoiceDocument);
        commitReturnDeliveryLocal = addConcreteClass("Возврат местному поставщику", invoiceReturnDeliveryLocal, commitOut);

        returnSaleWhole = addConcreteClass("Оптовый возврат", orderWarehouseInc, returnInner, commitInc, orderWhole, invoiceDocument);
        returnSaleInvoiceRetail = addConcreteClass("Возврат реализации по безналу", orderShopInc, returnInner, commitInc, orderRetail);
        returnSaleCheckRetail = addConcreteClass("Возврат реализации за наличный расчет", orderShopInc, returnInner, commitInc, orderRetail);

        obligation = addAbstractClass("Сертификат", namedObject, barcodeObject);
        coupon = addConcreteClass("Купон", obligation);
        giftObligation = addConcreteClass("Подарочный сертификат", obligation);

        saleDocumentObligation = addConcreteClass("Продажа сертификатов", orderRetail);
    }

    CustomClass saleDocumentObligation;
    CustomClass documentShopPrice;

    LP balanceSklFreeQuantity;
    LP articleFreeQuantity;

    protected void initProperties() {

        LP removePercent = addSFProp("((prm1*(100-prm2))/100)", DoubleClass.instance, 2);
        LP percent = addSFProp("(prm1*prm2/100)", DoubleClass.instance, 2);

        LP multiplyDouble2 = addMFProp(DoubleClass.instance, 2);

        LP positive = addJProp(greater2, 1, vzero);
        LP onlyPositive = addJProp(and1, 1, positive, 1);
        LP min = addSFProp("(prm1+prm2-ABS(prm1-prm2))/2", DoubleClass.instance, 2);

        LP articleToGroup = addDProp("articleToGroup", "Группа товаров", articleGroup, article);
        articleToGroupName = addJProp(baseGroup, "Группа товаров", name, articleToGroup, 1);

        incStore = addCUProp("incStore", "Склад (прих.)", // generics
                addDProp("incShop", "Магазин (прих.)", shop, orderShopInc),
                addDProp("incWarehouse", "Распред. центр (прих.)", warehouse, orderWarehouseInc));
        addJProp(baseGroup, "Склад (прих.)", name, incStore, 1);
        outStore = addCUProp("outCStore", "Склад (расх.)", // generics
                addDProp("outStore", "Склад (расх.)", store, orderStoreOut),
                addDProp("outShop", "Магазин (расх.)", shop, orderShopOut),
                addDProp("outWarehouse", "Распред. центр (расх.)", warehouse, orderWarehouseOut));
        addJProp(baseGroup, "Склад (расх.)", name, outStore, 1);

        computerShop = addDProp("Магазин рабочего места", shop, computer);
        addJProp(baseGroup, "Магазин рабочего места", name, computerShop, 1);

        orderSupplier = addCUProp("orderSupplier", "Поставщик", addDProp("localSupplier", "Местный поставщик", localSupplier, orderLocal),
                addDProp("importSupplier", "Импортный поставщик", importSupplier, orderDeliveryImport));

        LP outSubject = addCUProp(addJProp(and1, orderSupplier, 1, is(orderDelivery), 1), outStore);

        orderContragent = addCUProp("Контрагент", // generics
                orderSupplier,
                addDProp("wholeCustomer", "Оптовый покупатель", customerWhole, orderWhole),
                addDProp("retailCustomer", "Розничный покупатель", customerRetail, orderRetail));
        nameContragent = addJProp(baseGroup, "Контрагент", name, orderContragent, 1);

        LP sameContragent = addJProp(equals2, orderContragent, 1, orderContragent, 2);

        LP invoiceNumber = addDProp(baseGroup, "invoiceNumber", "Накладная", StringClass.get(20), invoiceDocument);

        outerOrderQuantity = addDProp(documentGroup, "extIncOrderQuantity", "Кол-во заяв.", DoubleClass.instance, orderDelivery, article);
        outerCommitedQuantity = addDProp(documentGroup, "extIncCommitedQuantity", "Кол-во принятое", DoubleClass.instance, commitDelivery, article);
//        outerCommitedQuantity.setDerivedChange(outerOrderQuantity, 1, 2, is(commitInc), 1); // пока не будем делать так как идет ручное штрих-кодирование
        LP expiryDate = addDProp(baseGroup, "expiryDate", "Срок годн.", DateClass.instance, commitDelivery, article);

        // для возвратных своего рода generics
        LP returnOuterQuantity = addDProp("returnDeliveryLocalQuantity", "Кол-во возврата", DoubleClass.instance, orderReturnDeliveryLocal, article, commitDeliveryLocal);

        returnInnerCommitQuantity = addCUProp(documentGroup, "Кол-во возврата", // generics
                         addDProp("returnSaleWholeQuantity", "Кол-во возврата", DoubleClass.instance, returnSaleWhole, article, commitDelivery, commitSaleWhole),
                         addDProp("returnSaleInvoiceRetailQuantity", "Кол-во возврата", DoubleClass.instance, returnSaleInvoiceRetail, article, commitDelivery, commitSaleInvoiceRetail),
                         addDProp("returnSaleCheckRetailQuantity", "Кол-во возврата", DoubleClass.instance, returnSaleCheckRetail, article, commitDelivery, commitSaleCheckRetail));

        LP orderInnerQuantity = addDProp("outOrderQuantity", "Кол-во операции", DoubleClass.instance, orderInner, article, commitDelivery);

        LP returnedInnerQuantity = addSGProp("Кол-во возвр. парт.", returnInnerCommitQuantity, 4, 2, 3);
        confirmedInnerQuantity = addDUProp("Кол-во подтв. парт.", addJProp(and1, orderInnerQuantity, 1, 2, 3, is(commitOut), 1) , returnedInnerQuantity);
        addConstraint(addJProp("Кол-во возврата должно быть не меньше кол-ва самой операции", greater2, vzero, confirmedInnerQuantity, 1, 2, 3), false);

        // для док. \ товара \ парт. \ док. прод.   - кол-во подтв. парт. если совпадают контрагенты
        returnInnerFreeQuantity = addJProp(documentGroup, "Макс. кол-во по возврату парт.", and1, addJProp(and1, confirmedInnerQuantity, 4, 2, 3, sameContragent, 1, 4), 1, 2, 3, 4, is(returnInner), 1) ;
        returnInnerQuantity = addDGProp(documentGroup, "Кол-во возврата", 2, false, returnInnerCommitQuantity, 1, 2, 4, returnInnerFreeQuantity, 1, 2, 3, 4, date, 3, 3);
        LP returnDocumentQuantity = addCUProp("Кол-во возврата", returnOuterQuantity, returnInnerQuantity); // возвратный документ\прямой документ
        addConstraint(addJProp("При возврате контрагент документа, по которому идет возврат, должен совпадать с контрагентом возврата", and1, addJProp(diff2, orderContragent, 1, orderContragent, 2), 1, 3,
                returnDocumentQuantity, 1, 2, 3), false);

        // инвентаризация
        innerBalanceCheck = addDProp(documentGroup, "innerBalanceCheck", "Остаток инв.", DoubleClass.instance, balanceCheck, article, commitDelivery);
        innerBalanceCheckDB = addDProp("innerBalanceCheckDB", "Остаток (по учету)", DoubleClass.instance, balanceCheck, article, commitDelivery);

        innerQuantity = addCUProp(documentGroup, "innerQuantity", "Кол-во операции", returnOuterQuantity, orderInnerQuantity,
                                addDGProp(2, false, returnInnerCommitQuantity, 1, 2, 3, returnInnerFreeQuantity, 1, 2, 3, 4, date, 4, 4),
                                addDUProp("balanceCheckQuantity","Кол-во инв.", innerBalanceCheckDB, innerBalanceCheck));

        LP incSklCommitedQuantity = addSGProp(moveGroup, "Кол-во прихода парт. на скл.",
                                    addCUProp(addJProp(and1, outerCommitedQuantity, 1, 2, equals2, 1, 3),
                                    addJProp(and1, innerQuantity, 1, 2, 3, is(commitInc), 1)), incStore, 1, 2, 3);

        LP outSklCommitedQuantity = addSGProp(moveGroup, "Кол-во отгр. парт. на скл.", addJProp("Кол-во отгр. парт.", and1, innerQuantity, 1, 2, 3, is(commitOut), 1), outStore, 1, 2, 3);
        LP outSklQuantity = addSGProp(moveGroup, "Кол-во заяв. парт. на скл.", innerQuantity, outStore, 1, 2, 3);

        balanceSklCommitedQuantity = addDUProp(moveGroup, "balanceSklCommitedQuantity", "Остаток парт. на скл.", incSklCommitedQuantity, outSklCommitedQuantity);
        balanceSklFreeQuantity = addDUProp(moveGroup, "balanceSklFreeQuantity", "Свободное кол-во на скл.", incSklCommitedQuantity, outSklQuantity);
        addConstraint(addJProp("Кол-во резерва должно быть не меньше нуля", greater2, vzero, balanceSklFreeQuantity, 1, 2, 3), false);

        articleFreeQuantity = addSGProp(moveGroup, "articleFreeQuantity", "Свободное кол-во на скл.", balanceSklFreeQuantity, 1, 2);

        innerBalanceCheckDB.setDerivedChange(balanceSklCommitedQuantity, outStore, 1, 2, 3);

        addJProp(moveGroup, "Остаток парт. прих.", balanceSklCommitedQuantity, incStore, 1, 2, 3);
        addJProp(moveGroup, "Остаток парт. расх.", balanceSklCommitedQuantity, outStore, 1, 2, 3);

        returnFreeQuantity = addSGProp(documentGroup, "Макс. кол-во по возврату", returnInnerFreeQuantity, 1, 2, 4);

        LP documentOutSklFreeQuantity = addJProp("Свободно парт. расх.", balanceSklFreeQuantity, outStore, 1, 2, 3);
        // создаем свойства ограничения для расчета себестоимости (являются следствием addConstraint)
        documentInnerFreeQuantity = addCUProp(documentMoveGroup, "Макс. кол-во по парт.",
                            addJProp(and1, documentOutSklFreeQuantity, 1, 2, 3, sameContragent, 1, 3), // возврата поставщику - ограничение что кол-во своб. (всегда меньше кол-во подтв.) + условие что партии этого поставщика
                            addJProp(and1, documentOutSklFreeQuantity, 1, 2, 3, is(orderInner), 1), // прямого расхода - кол-во свободного для этого склада
                            innerBalanceCheckDB, // для инвентаризации - не больше зафиксированного количества по учету
                            addSGProp(returnInnerFreeQuantity, 1, 2, 3)); // возврата расхода  - кол-во подтв. этого контрагента

        // добавляем свойства по товарам
        articleInnerQuantity = addDGProp(documentGroup, "articleInnerQuantity", "Кол-во операции", 2, false, innerQuantity, 1, 2, documentInnerFreeQuantity, 1, 2, 3, date, 3, 3);
        documentFreeQuantity = addSGProp(documentMoveGroup, "Макс. кол-во по товару", documentInnerFreeQuantity, 1, 2);

        articleQuantity = addCUProp("Кол-во операции", outerCommitedQuantity, articleInnerQuantity);
        articleOrderQuantity = addCUProp("Заяв. кол-во операции", outerOrderQuantity, articleInnerQuantity);

        // ожидаемый приход на склад
        articleFreeOrderQuantity = addSUProp("articleFreeOrderQuantity" , "Ожидаемое своб. кол-во", Union.SUM, articleFreeQuantity, addSGProp(moveGroup, "Ожидается приход", addJProp(andNot1, articleOrderQuantity, 1, 2, is(commitInc), 1), incStore, 1, 2)); // сумма по еще не пришедшим

        articleBalanceCheck = addDGProp(documentGroup, "articleBalanceCheck", "Остаток инв.", 2, false, innerBalanceCheck, 1, 2, innerBalanceCheckDB, 1, 2, 3, date, 3, 3);

        LP articleBalanceSklCommitedQuantity = addSGProp(moveGroup, "articleBalanceSklCommitedQuantity", "Остаток тов. на скл.", balanceSklCommitedQuantity, 1, 2);
        addJProp(documentMoveGroup, "Остаток тов. прих.", articleBalanceSklCommitedQuantity, incStore, 1, 2);
        addJProp(documentMoveGroup, "Остаток тов. расх.", articleBalanceSklCommitedQuantity, outStore, 1, 2);

        // цены
        LP shopFormat = addDProp("shopFormat", "Формат", format, shop);
        addJProp(baseGroup, "Формат", name, shopFormat, 1);

        currentRRP = addDProp(priceGroup, "currentRRP", "RRP", DoubleClass.instance, article);
        LP currentPriceRate = addDProp(priceGroup, "currentPriceRate", "Курс", DoubleClass.instance);
        LP currentFormatDiscount = addDProp(priceGroup, "currentFormatDiscount", "Скидка на формат", DoubleClass.instance, format);
        LP currentWarehouseDiscount = addDProp(priceGroup, "currentWarehouseDiscount", "Опт. скидка", DoubleClass.instance);
        LP currentPrice = addJProp(priceGroup, "Необх. цена", multiplyDouble2, currentRRP, 1, currentPriceRate);

        // простые акции
        LP actionFrom = addDProp(baseGroup, "actionFrom", "От", DateClass.instance, action);
        LP actionTo = addDProp(baseGroup, "actionTo", "До", DateClass.instance, action);
        LP actionDiscount = addDProp(baseGroup, "actionDiscount", "Скидка", DoubleClass.instance, action);
        inAction = addDProp(baseGroup, "inAction", "В акции", LogicalClass.instance, action, article);

        LP isStarted = addJProp(baseGroup, "Началась", and(true, true), is(action), 1,
                                        addJProp(less2, currentDate, actionFrom, 1), 1,
                                        addJProp(greater2, currentDate, actionTo, 1), 1);
        LP articleSaleAction = addCGProp(priceGroup, "articleAction", "Дейст. распродажа",
                addJProp(and1, 1, addJProp(and1, inAction, 1, 2, addJProp(and1, isStarted, 1, is(saleAction), 1), 1), 1, 2), inAction, 2);
        LP articleDiscount = addSUProp(Union.OVERRIDE, addCProp(DoubleClass.instance, 0, article), addJProp(priceGroup, "Тек. скидка", actionDiscount, articleSaleAction, 1));
        LP actionNoExtraDiscount = addDProp(baseGroup, "actionNoExtraDiscount", "Без доп. скидок", LogicalClass.instance, saleAction);

        LP articleActionToGroup = addDProp(baseGroup, "articleActionToGroup", "Группа акций", groupArticleAction, articleAction);

        LP articleActionHourFrom = addDProp(baseGroup, "articleActionHourFrom", "Час от", DoubleClass.instance, articleAction);
        LP articleActionHourTo = addDProp(baseGroup, "articleActionHourTo", "Час до", DoubleClass.instance, articleAction);
        LP articleActionClientSum = addDProp(baseGroup, "articleActionClientSum", "Нак. сумма от", DoubleClass.instance, articleAction);
        LP articleActionQuantity = addDProp(baseGroup, "articleActionQuantity", "Кол-во от", DoubleClass.instance, articleAction);

        // продажа облигаций
        obligationSaleDocument = addDProp(documentGroup, "obligationSaleDocument", "Документ продажи", saleDocumentObligation, giftObligation);

        LP obligationTo = addDProp(baseGroup, "obligationTo", "До", DateClass.instance, obligation);
        LP obligationSum = addDProp(baseGroup, "obligationSum", "Сумма", DoubleClass.instance, obligation);
        LP obligationSumFrom = addDProp(baseGroup, "obligationSumFrom", "Сумма покупки", DoubleClass.instance, obligation);

        LP couponMaxPercent = addDProp(baseGroup, "couponMaxPercent", "Макс. процент по купонам", DoubleClass.instance);

        LP currentStoreDiscount = addCUProp(priceGroup, "Скидка на складе",
                addJProp(and1, currentWarehouseDiscount, is(warehouse), 1),
                addJProp(currentFormatDiscount, shopFormat, 1));

        LP actionPrice = addJProp(priceGroup, "Акц. цена", removePercent, currentPrice, 1, articleDiscount, 1);
        LP requiredStorePrice = addJProp(priceGroup, "Необх. цена", removePercent, actionPrice, 2, currentStoreDiscount, 1);

        balanceFormatFreeQuantity = addSGProp(moveGroup, "Своб. кол-во по форм.", articleFreeQuantity, shopFormat, 1, 2);

        LP revalueShop = addDProp("revalueShop", "Магазин", shop, documentRevalue);
        addJProp(baseGroup, "Магазин", name, revalueShop, 1);
        priceStore = addCUProp("priceStore", "Склад (цены)", incStore, revalueShop);

        documentRevalued = addDProp(documentGroup, "isRevalued", "Переоц.", LogicalClass.instance, documentRevalue, article);
        inDocumentPrice = addCUProp("inDocumentPrice", "Изм. цены", documentRevalued, addJProp(and1, is(commitWholeShopInc), 1, articleQuantity, 1, 2));

        LP[] maxShopPriceProps = addMGProp((AbstractGroup)null, true, new String[]{"currentShopPriceDate","currentShopPriceDoc"}, new String[]{"Дата посл. цены в маг.","Посл. док. цены в маг."}, 1,
                addJProp(and1, date, 1, inDocumentPrice, 1, 2), 1, priceStore, 1, 2);
        currentShopPriceDate = maxShopPriceProps[0]; currentShopPriceDoc = maxShopPriceProps[1];

        shopPrice = addDProp(documentPriceGroup, "shopPrice", "Цена (док.)", DoubleClass.instance, documentShopPrice, article);
        shopPrice.setDerivedChange(requiredStorePrice, priceStore, 1, 2, inDocumentPrice, 1, 2);

        currentShopPrice = addJProp(priceGroup, "currentShopPrice", "Цена на складе (тек.)", shopPrice, currentShopPriceDoc, 1, 2, 2);

        LP outOfDatePrice = addJProp(and(false,false), vtrue, articleBalanceSklCommitedQuantity, 1, 2, addJProp(diff2, requiredStorePrice, 1, 2, currentShopPrice, 1, 2), 1, 2);
        documentRevalued.setDerivedChange(outOfDatePrice, priceStore, 1, 2);

        prevPrice = addDProp(documentPriceGroup, "prevPrice", "Цена пред.", DoubleClass.instance, documentShopPrice, article);
        prevPrice.setDerivedChange(currentShopPrice, priceStore, 1, 2, inDocumentPrice, 1, 2);
        revalBalance = addDProp(documentPriceGroup, "revalBalance", "Остаток переоц.", DoubleClass.instance, documentShopPrice, article);
        revalBalance.setDerivedChange(articleBalanceSklCommitedQuantity, priceStore, 1, 2, inDocumentPrice, 1, 2);

        isRevalued = addJProp(diff2, shopPrice, 1, 2, prevPrice, 1, 2); // для акта переоценки
        isNewPrice = addJProp(andNot1, inDocumentPrice, 1, 2, addJProp(equals2, shopPrice, 1, 2, prevPrice, 1, 2), 1, 2); // для ценников

        LP saleStorePrice = addCUProp(priceGroup, "Цена прод.", addJProp(and1, requiredStorePrice, 1, 2, is(warehouse), 1), currentShopPrice);

        NDS = addDProp(documentGroup, "NDS", "НДС", DoubleClass.instance, documentNDS, article);
        LP[] maxNDSProps = addMGProp((AbstractGroup)null, true, new String[]{"currentNDSDate","currentNDSDoc"}, new String[]{"Дата посл. НДС","Посл. док. НДС"}, 1,
                addJProp(and1, date, 1, NDS, 1, 2), 1, 2);
        currentNDSDate = maxNDSProps[0]; currentNDSDoc = maxNDSProps[1];
        currentNDS = addJProp(baseGroup, "Тек. НДС", NDS, currentNDSDoc, 1, 1);

        // блок с логистикой\управленческими характеристиками

        // текущая схема
        articleSupplier = addDProp("articleSupplier", "Поставщик товара", supplier, article);
        addJProp(logisticsGroup, "Поставщик товара", name, articleSupplier, 1);
        LP shopWarehouse = addDProp("storeWarehouse", "Распред. центр", warehouse, shop);
        addJProp(logisticsGroup, "Распред. центр", name, shopWarehouse, 1);
        LP articleSupplierPrice = addDProp(logisticsGroup, "articleSupplierPrice", "Цена поставок", DoubleClass.instance, article);
        LP supplierCycle = addDProp(logisticsGroup, "supplierCycle", "Цикл поставок", DoubleClass.instance, supplier);
        LP shopCycle = addDProp(logisticsGroup, "shopCycle", "Цикл распределения", DoubleClass.instance, shop);

        LP supplierToWarehouse = addDProp(logisticsGroup, "supplierToWarehouse", "Пост. на распред. центр", LogicalClass.instance, supplier);

        // абстрактный товар \ склад - поставщик
        articleStoreSupplier = addSUProp("articleStoreSupplier", "Пост. товара на склад", Union.OVERRIDE, addJProp(and1, articleSupplier, 2, is(store), 1),
                        addJProp(and1, shopWarehouse, 1, addJProp(supplierToWarehouse, articleSupplier, 1), 2));
        LP storeSupplierCycle = addCUProp(addJProp(and1, supplierCycle, 2, is(store), 1), addJProp(and1, shopCycle, 1, is(warehouse), 2));

        articleStorePeriod = addJProp("articleStorePeriod", "Цикл поставок на склад", storeSupplierCycle, 1, articleStoreSupplier, 1, 2);

        articleFormatToSell = addDProp(logisticsGroup, "articleFormatToSell", "В ассортименте", LogicalClass.instance, format, article);
        LP articleFormatMin = addDProp(logisticsGroup, "articleFormatMin", "Страх. запас", DoubleClass.instance, format, article);

        LP articleStoreToSell = addCUProp(logisticsGroup, "articleStoreToSell", "В ассортименте",addJProp(articleFormatToSell, shopFormat, 1, 2),
                                    addDProp("articleWarehouseToSell", "В ассортименте", LogicalClass.instance, warehouse, article));
        articleStoreMin = addJProp("articleStoreMin", "Страх. запас", and1, addCUProp(logisticsGroup, "Страх. запас", addJProp(articleFormatMin, shopFormat, 1, 2),
                                    addDProp("articleWarehouseMin", "Страх. запас", DoubleClass.instance, warehouse, article)), 1, 2, articleStoreToSell, 1, 2);
        LP articleStoreForecast = addJProp(and1, addDProp(logisticsGroup, "articleStoreForecast", "Прогноз прод. (в день)", DoubleClass.instance, store, article), 1, 2, articleStoreToSell, 1, 2);

        // MAX((страховой запас+прогноз расхода до следующего цикла поставки)-остаток,0) (по внутренним складам)
        articleFullStoreDemand = addSUProp("articleFullStoreDemand", "Общ. необходимость", Union.SUM, addJProp(multiplyDouble2, addSupplierProperty(articleStoreForecast), 1, 2, articleStorePeriod, 1, 2), addSupplierProperty(articleStoreMin));
        LP articleStoreRequired = addJProp(onlyPositive, addDUProp(articleFullStoreDemand, addSupplierProperty(articleFreeOrderQuantity)), 1, 2);

        documentLogisticsRequired = addJProp(documentLogisticsGroup, "Необходимо", articleStoreRequired, incStore, 1, 2);
        documentLogisticsSupplied = addJProp(documentLogisticsGroup, "Поставляется", equals2, outSubject, 1, addJProp(articleStoreSupplier, incStore, 1, 2), 1, 2);
        documentLogisticsRecommended = addJProp(documentLogisticsGroup, "Рекомендовано", min, documentLogisticsRequired, 1, 2, documentFreeQuantity, 1, 2);

        orderClientSum = addSUProp("Нак. сумма", Union.OVERRIDE, addCProp(DoubleClass.instance, 0, orderSaleRetail), addDProp(baseGroup, "orderClientSum", DoubleClass.instance, orderSaleRetail));
        LP orderHour = addDProp(baseGroup, "orderHour", "Час", DoubleClass.instance, orderSaleRetail);
        orderHour.setDerivedChange(currentHour, is(orderSale), 1);

        changeQuantityTime = addTCProp(Time.EPOCH, "changeQuantityTime", "Время выбора", articleInnerQuantity, orderSaleRetail, article);
        changeQuantityOrder = addOProp(documentGroup, "Номер", addCProp(IntegerClass.instance, 1, orderSaleRetail, article), false, true, true, 1, 1, changeQuantityTime, 1, 2);

        reverseRetailBarcode = addDProp("reverseRetailBarcode", "Реверс", LogicalClass.instance, orderSaleRetail);

        LP orderSaleDocPrice = addDProp("orderSalePrice", "Цена прод.", DoubleClass.instance, orderSale, article);
        orderSaleDocPrice.setDerivedChange(saleStorePrice, outStore, 1, 2, articleQuantity, 1, 2);
        orderSalePrice = addSUProp(documentPriceGroup, "Цена прод.", Union.OVERRIDE, addJProp(and1, addJProp(saleStorePrice, outStore, 1, 2), 1, 2, is(orderSale), 1), orderSaleDocPrice);

        LP articleActionActive = addJProp(and(false, false, false, false, true, true, true, true, true), articleQuantity, 1, 2, is(orderSaleRetail), 1, is(articleAction), 3, inAction, 3, 2, isStarted, 3,
                                        addJProp(less2, articleQuantity, 1, 2, articleActionQuantity, 3), 1, 2, 3,
                                        addJProp(actionNoExtraDiscount, articleSaleAction, 1), 2,
                                        addJProp(less2, orderClientSum, 1, articleActionClientSum, 2), 1, 3,
                                        addJProp(less2, orderHour, 1, articleActionHourFrom, 2), 1, 3,
                                        addJProp(greater2, orderHour, 1, articleActionHourTo, 2), 1, 3);
        orderArticleSaleDiscount = addDProp(baseGroup, "orderArticleSaleDiscount", "Скидка", DoubleClass.instance, orderSaleRetail, article);
        orderArticleSaleDiscount.setDerivedChange(addSGProp(addMGProp(addJProp(and1, actionDiscount, 3, articleActionActive, 1, 2, 3), 1, 2, articleActionToGroup, 3), 1, 2), true, 1, 2, is(orderSaleRetail), 1);

        LP orderArticleSaleSum = addJProp(documentPriceGroup, "Сумма прод.", multiplyDouble2, articleQuantity, 1, 2, orderSaleDocPrice, 1, 2);
        LP orderArticleSaleDiscountSum = addJProp(documentPriceGroup, "Сумма скидки", percent, orderArticleSaleSum, 1, 2, orderArticleSaleDiscount, 1, 2);
        orderArticleSaleSumWithDiscount = addDUProp(documentPriceGroup, "Сумма со скидкой", orderArticleSaleSum, orderArticleSaleDiscountSum);
        orderSaleDiscountSum = addSGProp(documentAggrPriceGroup, "Сумма скидки", orderArticleSaleDiscountSum, 1);
        orderSalePay = addDUProp(documentAggrPriceGroup, "Сумма к оплате", addSGProp("Сумма док. прод.", orderArticleSaleSum, 1), orderSaleDiscountSum);

        LP returnArticleSaleSum = addJProp(documentPriceGroup, "Сумма возвр.", multiplyDouble2, returnInnerQuantity, 1, 2, 3, orderSaleDocPrice, 3, 2);
        LP returnArticleSaleDiscount = addJProp(documentPriceGroup, "Сумма скидки возвр.", percent, returnArticleSaleSum, 1, 2, 3, orderArticleSaleDiscount, 3, 2);
        LP returnSaleDiscount = addSGProp(documentPriceGroup, "Сумма скидки возвр.", returnArticleSaleDiscount, 1);
        LP returnSalePay = addDUProp(documentPriceGroup, "Сумма к возвр.", addSGProp("Сумма возвр.", returnArticleSaleSum, 1), returnSaleDiscount);

        clientSum = addSGProp(baseGroup, "clientSum", "Нак. сумма", orderSalePay, orderContragent, 1);
        orderClientSum.setDerivedChange(clientSum, orderContragent, 1);

        LP orderDeliveryPrice = addDProp("orderDeliveryPrice", "Цена закуп.", DoubleClass.instance, orderDelivery, article);
        orderDeliveryPrice.setDerivedChange(articleSupplierPrice, 2, articleQuantity, 1, 2);
        addSUProp(documentPriceGroup, "Цена закуп.", Union.OVERRIDE, addJProp(and1, articleSupplierPrice, 2, is(orderDelivery), 1), orderDeliveryPrice);

        orderSaleUseObligation = addDProp(documentPriceGroup, "orderSaleUseObligation", "Использовать", LogicalClass.instance, commitSaleCheckRetail, obligation);
        obligationDocument = addCGProp(null, "obligationDocument", "Исп. документ", addJProp(and1, 1, orderSaleUseObligation, 1, 2), orderSaleUseObligation, 2);
        orderSaleObligationCanNotBeUsed = addJProp(and(false, false, true), is(commitSaleCheckRetail), 1, is(obligation), 2,
                                                    addJProp(less2, orderSalePay, 1, obligationSumFrom, 2), 1, 2,
                                                    addJProp(greater2, date, 1, obligationTo, 2), 1, 2);
        addConstraint(addJProp("Нельзя использовать выбранный сертификат", and1, orderSaleUseObligation, 1, 2, orderSaleObligationCanNotBeUsed, 1, 2), false);
        LP obligationUseSum = addJProp(and1, obligationSum, 2, orderSaleUseObligation, 1, 2);
        LP orderSalePayCoupon = addSGProp(addJProp(and1, obligationUseSum, 1, 2, is(coupon), 2), 1);
        LP orderSalePayGiftObligation = addSGProp(addJProp(and1, obligationUseSum, 1, 2, is(giftObligation), 2), 1);
        LP orderSalePayObligation = addSUProp(Union.SUM, orderSalePayGiftObligation, addJProp(min, orderSalePayCoupon, 1, addJProp(percent, orderSalePay, 1, couponMaxPercent), 1));

        orderSalePayCash = addDProp(documentPriceGroup, "orderSalePayCash", "Наличными", DoubleClass.instance, commitSaleCheckRetail);
        orderSalePayCard = addDProp(documentPriceGroup, "orderSalePayCard", "Карточкой", DoubleClass.instance, commitSaleCheckRetail);

        // сдача/доплата
        orderSaleDiff = addDUProp(documentAggrPriceGroup, "Разница",
                addJProp(onlyPositive, addDUProp(orderSalePay, addSUProp(Union.SUM, orderSalePayCard, orderSalePayObligation)), 1),
                orderSalePayCash);
    }

    private LP addSupplierProperty(LP property) {
        return addSUProp(Union.SUM, property, addSGProp(property, articleStoreSupplier, 1, 2, 2));
    }

    LP orderClientSum;
    LP orderArticleSaleSumWithDiscount;
    LP orderSalePrice;
    LP changeQuantityOrder;
    LP computerShop;
    LP orderSalePayCash;
    LP orderSalePayCard;
    LP reverseRetailBarcode;
    LP changeQuantityTime;
    LP confirmedInnerQuantity;
    LP obligationSaleDocument;
    LP obligationDocument;
    LP orderSaleObligationCanNotBeUsed;
    LP orderSaleUseObligation;

    LP articleFormatToSell;
    LP inAction;
    LP clientSum;
    LP incStore;
    LP outStore;

    LP orderContragent;
    LP orderSupplier;

    LP articleFreeOrderQuantity;

    LP articleToGroupName;

    LP articleSupplier;
    LP articleStoreSupplier;
    LP articleStorePeriod;
    LP articleStoreMin;
    LP articleFullStoreDemand;

    LP nameContragent;

    LP documentLogisticsSupplied, documentLogisticsRequired, documentLogisticsRecommended;
    LP currentNDSDate, currentNDSDoc, currentNDS, NDS;
    LP articleQuantity, prevPrice, revalBalance;
    LP articleOrderQuantity;
    LP orderSaleDiscountSum, orderSalePay, orderSaleDiff;
    LP orderArticleSaleDiscount;
    LP shopPrice;
    LP priceStore, inDocumentPrice;
    LP isRevalued, isNewPrice, documentRevalued;
    LP balanceFormatFreeQuantity;
    LP currentShopPriceDate;
    LP currentShopPriceDoc;
    LP currentShopPrice;
    LP currentRRP;

    LP documentFreeQuantity, documentInnerFreeQuantity, returnInnerFreeQuantity, returnFreeQuantity, innerQuantity, returnInnerCommitQuantity, returnInnerQuantity;
    LP outerOrderQuantity, outerCommitedQuantity, articleBalanceCheck, articleBalanceCheckDB, innerBalanceCheck, innerBalanceCheckDB, balanceSklCommitedQuantity;
    LP articleInnerQuantity;

    AbstractGroup documentGroup;
    AbstractGroup priceGroup;
    AbstractGroup moveGroup;
    AbstractGroup allGroup;
    AbstractGroup logisticsGroup;
    AbstractGroup documentMoveGroup;
    AbstractGroup documentPriceGroup, documentAggrPriceGroup;
    AbstractGroup documentLogisticsGroup;

    protected void initGroups() {
        allGroup = new AbstractGroup("Все");
        allGroup.createContainer = false;
        allGroup.add(baseGroup);

        documentGroup = new AbstractGroup("Параметры документа");
        allGroup.add(documentGroup);

        moveGroup = new AbstractGroup("Движение товаров");
        allGroup.add(moveGroup);

        documentMoveGroup = new AbstractGroup("Текущие параметры документа");
        documentGroup.add(documentMoveGroup);

        priceGroup = new AbstractGroup("Ценовые параметры");
        allGroup.add(priceGroup);

        documentPriceGroup = new AbstractGroup("Ценовые параметры документа");
        documentPriceGroup.createContainer = false;
        documentGroup.add(documentPriceGroup);

        documentAggrPriceGroup = new AbstractGroup("Агрегированные ценовые параметры документа");
        documentAggrPriceGroup.createContainer = false;
        documentPriceGroup.add(documentAggrPriceGroup);

        logisticsGroup = new AbstractGroup("Логистические параметры");
        allGroup.add(logisticsGroup);

        documentLogisticsGroup = new AbstractGroup("Логистические параметры документа");
        documentGroup.add(documentLogisticsGroup);
    }

    protected void initConstraints() {
    }

    protected void initPersistents() {
        persistents.add((AggregateProperty) balanceSklCommitedQuantity.property);
        persistents.add((AggregateProperty) balanceSklFreeQuantity.property);

        persistents.add((AggregateProperty) articleFreeQuantity.property);
        persistents.add((AggregateProperty) articleFreeOrderQuantity.property);

        persistents.add((AggregateProperty) incStore.property);
        persistents.add((AggregateProperty) outStore.property);
        persistents.add((AggregateProperty) orderSupplier.property);

        persistents.add((AggregateProperty) priceStore.property);
        persistents.add((AggregateProperty) inDocumentPrice.property);

        persistents.add((AggregateProperty) currentShopPriceDate.property);
        persistents.add((AggregateProperty) currentShopPriceDoc.property);

        persistents.add((AggregateProperty) currentNDSDate.property);
        persistents.add((AggregateProperty) currentNDSDoc.property);

        persistents.add((AggregateProperty) innerQuantity.property);

        persistents.add((AggregateProperty) clientSum.property);

        // все связанное с ассортиментами чтобы веселее работало
        persistents.add((AggregateProperty) articleStoreSupplier.property);
        persistents.add((AggregateProperty) articleStorePeriod.property);
        persistents.add((AggregateProperty) articleStoreMin.property);
        persistents.add((AggregateProperty) articleFullStoreDemand.property);
    }

    protected void initTables() {
        tableFactory.include("article",article);
        tableFactory.include("orders", order);
        tableFactory.include("store",store);
        tableFactory.include("localsupplier",localSupplier);
        tableFactory.include("importsupplier",importSupplier);
        tableFactory.include("customerwhole",customerWhole);
        tableFactory.include("customerretail",customerRetail);
        tableFactory.include("articlestore",article,store);
        tableFactory.include("articleorder",article,order);
        tableFactory.include("rates",DateClass.instance);
    }

    protected void initIndexes() {
    }

    protected void initNavigators() throws JRException, FileNotFoundException {

        NavigatorElement print = new NavigatorElement(baseElement, 4000, "Печатные формы");
            NavigatorForm incomePrice = addNavigatorForm(new IncomePriceNavigatorForm(print, 4100));
            NavigatorForm revalueAct = addNavigatorForm(new RevalueActNavigatorForm(print, 4200));
            NavigatorForm pricers = addNavigatorForm(new PricersNavigatorForm(print, 4300));

        NavigatorElement delivery = new NavigatorElement(baseElement, 1000, "Управление закупками");
            addNavigatorForm(new SupplierArticleNavigatorForm(delivery, 1050));
            NavigatorForm deliveryShopLocal = addNavigatorForm(new DeliveryShopLocalNavigatorForm(delivery, true, 1100));
                NavigatorForm deliveryShopLocalBrowse = addNavigatorForm(new DeliveryShopLocalNavigatorForm(deliveryShopLocal, false, 1125));
            NavigatorForm deliveryWarehouseLocal = addNavigatorForm(new DeliveryWarehouseLocalNavigatorForm(delivery, true, 1130));
                NavigatorForm deliveryWarehouseLocalBrowse = addNavigatorForm(new DeliveryWarehouseLocalNavigatorForm(deliveryWarehouseLocal, false, 1135));
            NavigatorForm deliveryImport = addNavigatorForm(new DeliveryImportNavigatorForm(delivery, true, 1150));
                NavigatorForm deliveryImportBrowse = addNavigatorForm(new DeliveryImportNavigatorForm(deliveryImport, false, 1175));
            NavigatorForm returnDelivery = addNavigatorForm(new ReturnDeliveryLocalNavigatorForm(delivery, 1400, true));
                addNavigatorForm(new ReturnDeliveryLocalNavigatorForm(returnDelivery, 1410, false));

        NavigatorElement sale = new NavigatorElement(baseElement, 1200, "Управление продажами");
            NavigatorForm saleDocumentObligation = addNavigatorForm(new SaleDocumentObligationNavigatorForm(sale, 1225, true));
                addNavigatorForm(new SaleDocumentObligationNavigatorForm(saleDocumentObligation, 1235, false));
            NavigatorForm saleWhole = addNavigatorForm(new SaleWholeNavigatorForm(sale, 1250, true));
                addNavigatorForm(new SaleWholeNavigatorForm(saleWhole, 1275, false));
            NavigatorForm saleRetail = addNavigatorForm(new SaleRetailNavigatorForm(sale, 1300, true));
                addNavigatorForm(new SaleRetailNavigatorForm(saleRetail, 1310, false));
            addNavigatorForm(new CommitSaleCheckRetailNavigatorForm(sale, 1320));
            NavigatorForm returnSaleWholeArticle = addNavigatorForm(new ReturnSaleWholeArticleNavigatorForm(sale, true, 1450));
                addNavigatorForm(new ReturnSaleWholeArticleNavigatorForm(returnSaleWholeArticle, false, 1460));
            NavigatorForm returnSaleCheckRetailArticle = addNavigatorForm(new ReturnSaleCheckRetailArticleNavigatorForm(sale, true, 1475));
                addNavigatorForm(new ReturnSaleCheckRetailArticleNavigatorForm(returnSaleCheckRetailArticle, false, 1485));
            NavigatorForm returnSaleInvoiceRetailArticle = addNavigatorForm(new ReturnSaleInvoiceRetailArticleNavigatorForm(sale, true, 1477));
                addNavigatorForm(new ReturnSaleInvoiceRetailArticleNavigatorForm(returnSaleInvoiceRetailArticle, false, 1487));

        NavigatorElement distribute = new NavigatorElement(baseElement, 3000, "Управление распределением");
            NavigatorForm distributeShopForm = addNavigatorForm(new DistributeShopNavigatorForm(distribute, 3100, true));
                NavigatorForm distributeShopBrowseForm = addNavigatorForm(new DistributeShopNavigatorForm(distributeShopForm, 3200, false));
            NavigatorForm distributeWarehouseForm = addNavigatorForm(new DistributeWarehouseNavigatorForm(distribute, 3110, true));
                NavigatorForm distributeWarehouseBrowseForm = addNavigatorForm(new DistributeWarehouseNavigatorForm(distributeWarehouseForm, 3210, false));

        NavigatorElement price = new NavigatorElement(baseElement, 2400, "Управление ценообразованием");
            NavigatorForm documentRevalue = addNavigatorForm(new DocumentRevalueNavigatorForm(price, true, 2650));
                addNavigatorForm(new DocumentRevalueNavigatorForm(documentRevalue, false, 2750));
            addNavigatorForm(new FormatArticleNavigatorForm(price, 2200));
            addNavigatorForm(new GlobalNavigatorForm(price, 5200));

        NavigatorElement tax = new NavigatorElement(baseElement, 5400, "Управление налогами");
            NavigatorForm nds = addNavigatorForm(new DocumentNDSNavigatorForm(tax, true, 5800));
                addNavigatorForm(new DocumentNDSNavigatorForm(nds, false, 5850));

        NavigatorElement actions = new NavigatorElement(baseElement, 7400, "Управление акциями");
            NavigatorForm saleAction = addNavigatorForm(new ActionNavigatorForm(actions, 7800));

        NavigatorElement balance = new NavigatorElement(baseElement, 1500, "Управление хранением");
            NavigatorForm balanceCheck = addNavigatorForm(new BalanceCheckNavigatorForm(balance, 1350, true));
                addNavigatorForm(new BalanceCheckNavigatorForm(balanceCheck, 1375, false));

        NavigatorElement store = new NavigatorElement(baseElement, 2000, "Сводная информация");
            addNavigatorForm(new StoreArticleNavigatorForm(store, 2100));

        commitWholeShopInc.addRelevant(incomePrice);
        documentShopPrice.addRelevant(revalueAct);
        documentShopPrice.addRelevant(pricers);
    }

    private class GlobalNavigatorForm extends NavigatorForm {
        protected GlobalNavigatorForm(NavigatorElement parent, int ID) {
            super(parent, ID, "Глобальные параметры");
            addPropertyView(properties, allGroup, true);
        }
    }

    private class BarcodeNavigatorForm extends NavigatorForm {

        ObjectNavigator objBarcode;

        private BarcodeNavigatorForm(NavigatorElement parent, int iID, String caption) {
            super(parent, iID, caption);

            objBarcode = addSingleGroupObjectImplement(StringClass.get(13), "Штрих-код", properties, baseGroup, true);
            objBarcode.groupTo.initClassView = ClassViewType.PANEL;
            objBarcode.groupTo.banClassView = ClassViewType.GRID | ClassViewType.HIDE;
        }
    }

    private class DocumentNavigatorForm extends BarcodeNavigatorForm {
        final ObjectNavigator objDoc;

        protected boolean toAdd = false;

        protected static final boolean fixFilters = true;
        protected static final boolean noOuters = true;

        protected Object[] getDocumentProps() {
            return new Object[] {baseGroup, true, documentGroup, true};
        }

        protected DocumentNavigatorForm(NavigatorElement parent, int ID, CustomClass documentClass, boolean toAdd) {
            super(parent, ID, (toAdd?documentClass.caption:"Документы"));

            this.toAdd = toAdd;

            objDoc = addSingleGroupObjectImplement(documentClass, "Документ", properties, getDocumentProps());
            if(toAdd) {
                objDoc.groupTo.initClassView = ClassViewType.PANEL;
                objDoc.groupTo.banClassView = ClassViewType.GRID;
                objDoc.show = false;
                objDoc.addOnTransaction = true;
            }
        }
    }

    private abstract class ArticleNavigatorForm extends DocumentNavigatorForm {
        final ObjectNavigator objArt;

        protected abstract PropertyObjectNavigator getCommitedQuantity();

        protected Object[] getArticleProps() {
            return new Object[] {baseGroup, true};
        }

        protected Object[] getDocumentArticleProps() {
            return new Object[] {baseGroup, true, documentGroup, true};
        }

        protected ArticleNavigatorForm(NavigatorElement parent, int ID, CustomClass documentClass, boolean toAdd, boolean filled) {
            super(parent, ID, documentClass, toAdd);

            objArt = addSingleGroupObjectImplement(article, "Товар", properties, getArticleProps());
            addPropertyView(objDoc, objArt, properties, getDocumentArticleProps());

            RegularFilterGroupNavigator filterGroup = new RegularFilterGroupNavigator(IDShift(1));
            filterGroup.addFilter(new RegularFilterNavigator(IDShift(1),
                                  getDocumentArticleFilter(),
                                  "Документ",
                                  KeyStroke.getKeyStroke(KeyEvent.VK_F10, 0)), !toAdd || filled);
            fillExtraFilters(filterGroup, toAdd && !filled);
            addRegularFilterGroup(filterGroup);

//            addHintsNoUpdate(properties, moveGroup);

            addBarcode(article, getCommitedQuantity());
        }

        @Override
        public DefaultFormView createDefaultRichDesign() {

            DefaultFormView form = super.createDefaultRichDesign();
            form.get(objArt.groupTo).gridView.constraints.fillVertical = 3;
            return form;
        }

        protected ArticleNavigatorForm(NavigatorElement parent, int ID, CustomClass documentClass, boolean toAdd) {
            this(parent, ID, documentClass, toAdd, false);
        }

        protected abstract FilterNavigator getDocumentArticleFilter();

        protected void fillExtraFilters(RegularFilterGroupNavigator filterGroup, boolean toAdd) {
        }

        // такое дебильное множественное наследование
        public void fillExtraLogisticsFilters(RegularFilterGroupNavigator filterGroup, boolean toAdd) {
            filterGroup.addFilter(new RegularFilterNavigator(IDShift(1),
                                  new NotNullFilterNavigator(getPropertyImplement(documentLogisticsSupplied)),
                                  "Поставляется",
                                  KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0)), toAdd);
            filterGroup.addFilter(new RegularFilterNavigator(IDShift(1),
                                  new NotNullFilterNavigator(getPropertyImplement(documentLogisticsRequired)),
                                  "Необходимо",
                                  KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0)));
        }
    }

    // для те которые не различают заказано и выполнено
    private abstract class ArticleNoCheckNavigatorForm extends ArticleNavigatorForm {

        protected FilterNavigator getDocumentArticleFilter() {
            return new NotNullFilterNavigator(getCommitedQuantity());
        }

        protected ArticleNoCheckNavigatorForm(NavigatorElement parent, int ID, CustomClass documentClass, boolean toAdd) {
            super(parent, ID, documentClass, toAdd);
        }

        protected ArticleNoCheckNavigatorForm(NavigatorElement parent, int ID, CustomClass documentClass, boolean toAdd, boolean filled) {
            super(parent, ID, documentClass, toAdd, filled);
        }
    }

    private abstract class InnerNavigatorForm extends ArticleNoCheckNavigatorForm {

        protected PropertyObjectNavigator getCommitedQuantity() {
            return addPropertyObjectImplement(articleInnerQuantity, objDoc, objArt);
        }

        @Override
        protected void fillExtraFilters(RegularFilterGroupNavigator filterGroup, boolean toAdd) {
            if(!fixFilters)
                filterGroup.addFilter(new RegularFilterNavigator(IDShift(1),
                                  new NotNullFilterNavigator(getPropertyImplement(documentFreeQuantity)),
                                  "Макс. кол-во",
                                  KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0)), toAdd);
        }

        protected InnerNavigatorForm(NavigatorElement parent, int ID, CustomClass documentClass, boolean toAdd, boolean filled) {
            super(parent, ID, documentClass, toAdd, filled);

            if(fixFilters)
                addFixedFilter(new OrFilterNavigator(getDocumentArticleFilter(),new NotNullFilterNavigator(addPropertyObjectImplement(documentFreeQuantity, objDoc, objArt))));
        }
    }

    private abstract class OuterNavigatorForm extends ArticleNavigatorForm {

        protected PropertyObjectNavigator getCommitedQuantity() {
            return getPropertyImplement(outerCommitedQuantity);
        }

        protected PropertyObjectNavigator getOrderQuantity() {
            return getPropertyImplement(outerOrderQuantity);
        }

        protected FilterNavigator getDocumentArticleFilter() {
            return new OrFilterNavigator(new NotNullFilterNavigator(getOrderQuantity()), new NotNullFilterNavigator(getCommitedQuantity()));
        }

        protected OuterNavigatorForm(NavigatorElement parent, int ID, boolean toAdd, CustomClass documentClass) {
            super(parent, ID, documentClass, toAdd);

            addBarcode(article, getOrderQuantity());
        }

        @Override
        protected void fillExtraFilters(RegularFilterGroupNavigator filterGroup, boolean toAdd) {
            fillExtraLogisticsFilters(filterGroup, toAdd);
        }
    }

    private class DeliveryShopLocalNavigatorForm extends OuterNavigatorForm {
        public DeliveryShopLocalNavigatorForm(NavigatorElement parent, boolean toAdd, int ID) {
            super(parent, ID, toAdd, orderDeliveryShopLocal);
        }
    }

    private class DeliveryWarehouseLocalNavigatorForm extends OuterNavigatorForm {
        public DeliveryWarehouseLocalNavigatorForm (NavigatorElement parent, boolean toAdd, int ID) {
            super(parent, ID, toAdd, orderDeliveryWarehouseLocal);
        }
    }

    private class DeliveryImportNavigatorForm extends OuterNavigatorForm {
        public DeliveryImportNavigatorForm(NavigatorElement parent, boolean toAdd, int ID) {
            super(parent, ID, toAdd, orderDeliveryImport);
        }
    }

    private class ArticleOuterNavigatorForm extends InnerNavigatorForm {
        ObjectNavigator objOuter;

        protected ArticleOuterNavigatorForm(NavigatorElement parent, int ID, CustomClass documentClass, boolean toAdd, CustomClass commitClass, boolean filled) {
            super(parent, ID, documentClass, toAdd, filled);

            if(!noOuters) {
                objOuter = addSingleGroupObjectImplement(commitClass, "Партия", properties, baseGroup, true);
                addPropertyView(objOuter, objDoc, properties, baseGroup, true, documentGroup, true);
                addPropertyView(objOuter, objDoc, objArt, properties, baseGroup, true, documentGroup, true);
                addPropertyView(objOuter, objArt, properties, baseGroup, true);

                NotNullFilterNavigator documentFilter = new NotNullFilterNavigator(getPropertyImplement(innerQuantity));
                NotNullFilterNavigator documentFreeFilter = new NotNullFilterNavigator(getPropertyImplement(documentInnerFreeQuantity));
                if(fixFilters)
                        addFixedFilter(new OrFilterNavigator(documentFilter, documentFreeFilter));
                RegularFilterGroupNavigator filterGroup = new RegularFilterGroupNavigator(IDShift(1));
                filterGroup.addFilter(new RegularFilterNavigator(IDShift(1),
                                      documentFilter,
                                      "Документ",
                                      KeyStroke.getKeyStroke(KeyEvent.VK_F10, 0)),!toAdd || filled);
                if(!fixFilters)
                   filterGroup.addFilter(new RegularFilterNavigator(IDShift(1),
                                      documentFreeFilter,
                                      "Макс. кол-во",
                                      KeyStroke.getKeyStroke(KeyEvent.VK_F9, 0)),toAdd && !filled);
                addRegularFilterGroup(filterGroup);
            }
        }
    }

    private class ReturnDeliveryLocalNavigatorForm extends ArticleOuterNavigatorForm {
        public ReturnDeliveryLocalNavigatorForm(NavigatorElement parent, int ID, boolean toAdd) {
            super(parent, ID, orderReturnDeliveryLocal, toAdd, commitDeliveryLocal, false);
        }
    }

    private class ArticleInnerNavigatorForm extends ArticleOuterNavigatorForm {

        protected ArticleInnerNavigatorForm(NavigatorElement parent, int ID, boolean toAdd, CustomClass documentClass, boolean filled) {
            super(parent, ID, documentClass, toAdd, commitDelivery, filled);
        }
    }

    private class DocumentInnerNavigatorForm extends ArticleInnerNavigatorForm {

        protected DocumentInnerNavigatorForm(NavigatorElement parent, int ID, boolean toAdd, CustomClass documentClass, boolean filled) {
            super(parent, ID, toAdd, documentClass, filled);
        }
    }

    private class SaleWholeNavigatorForm extends DocumentInnerNavigatorForm {
        public SaleWholeNavigatorForm(NavigatorElement parent, int ID, boolean toAdd) {
            super(parent, ID, toAdd, orderSaleWhole, false);
        }
    }

    private class SaleRetailNavigatorForm extends DocumentInnerNavigatorForm {

        private ObjectNavigator objObligation;

        public SaleRetailNavigatorForm(NavigatorElement parent, int ID, boolean toAdd) {
            this(parent, ID, orderSaleRetail, toAdd);
        }

        @Override
        protected Object[] getDocumentProps() {
            return new Object[] {nameContragent, orderClientSum, reverseRetailBarcode, orderSalePay, orderSaleDiscountSum, orderSalePayCash, orderSalePayCard, orderSaleDiff};
        }

        @Override
        protected Object[] getArticleProps() {
            return new Object[] {};
        }

        @Override
        protected Object[] getDocumentArticleProps() {
            return new Object[] {};
        }

        protected SaleRetailNavigatorForm(NavigatorElement parent, int ID, CustomClass documentClass, boolean toAdd) {
            super(parent, ID, toAdd, documentClass, true);

            // чтобы в порядке нужном
            addPropertyView(changeQuantityOrder, objDoc, objArt);
            addPropertyView(name, objArt);
            addPropertyView(articleQuantity, objDoc, objArt);
            addPropertyView(documentFreeQuantity, objDoc, objArt);
            addPropertyView(orderSalePrice, objDoc, objArt);
            addPropertyView(orderArticleSaleDiscount, objDoc, objArt);
            addPropertyView(orderArticleSaleSumWithDiscount, objDoc, objArt);

            objDoc.groupTo.banClassView |= ClassViewType.HIDE;

            objObligation = addSingleGroupObjectImplement(obligation, "Сертификат", properties, baseGroup, true);
            addPropertyView(objDoc, objObligation, properties, documentGroup, true);
            addHintsNoUpdate(obligationDocument);

//            addFixedFilter(new NotFilterNavigator(new NotNullFilterNavigator(addPropertyObjectImplement(obligationDocument, objObligation))));
//            addFixedFilter(new NotFilterNavigator(new NotNullFilterNavigator(addPropertyObjectImplement(orderSaleObligationCanNotBeUsed, objDoc, objObligation))));
            addFixedFilter(new NotNullFilterNavigator(addPropertyObjectImplement(orderSaleUseObligation, objDoc, objObligation)));

            PropertyObjectNavigator shopImplement = addPropertyObjectImplement(computerShop, CurrentComputerNavigator.instance);
            addFixedFilter(new CompareFilterNavigator(addPropertyObjectImplement(outStore, objDoc), Compare.EQUALS, shopImplement));
//            addFixedFilter(new OrFilterNavigator(new CompareFilterNavigator(addPropertyObjectImplement(outStore, objDoc), Compare.EQUALS, shopImplement),
//                                                    new NotFilterNavigator(new NotNullFilterNavigator(shopImplement))));

            addFixedOrder(addPropertyObjectImplement(changeQuantityTime, objDoc, objArt), false);

            addBarcode(obligation, orderSaleUseObligation);
            addBarcode(customerRetail, addPropertyObjectImplement(orderContragent, objDoc));
            reverseBarcode = getPropertyImplement(reverseRetailBarcode);

            addAutoAction(objBarcode, addPropertyObjectImplement(barcodeAction, objBarcode));
        }

        @Override
        public DefaultFormView createDefaultRichDesign() {

            DefaultFormView design = super.createDefaultRichDesign();

            // делаем, чтобы суммы были как можно правее
            design.get(getPropertyView(orderSalePay.property)).getContainer().constraints.directions = new SimplexComponentDirections(0.1,-0.1,0,0.1);

            if (toAdd) {

                // устанавливаем дизайн
                design.setFont(documentPriceGroup, new Font("Tahoma", Font.BOLD, 32), objDoc.groupTo);
                design.setBackground(documentAggrPriceGroup, new Color(240,240,240), objDoc.groupTo);

                // блокируем объекты для ввода
                design.setEnabled(documentAggrPriceGroup, false, objDoc.groupTo);
            }

            // привязываем функциональные кнопки
            design.get(getPropertyView(nameContragent.property, objDoc.groupTo)).editKey = KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0);
            design.get(objBarcode).objectCellView.editKey = KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0);
            design.get(getPropertyView(reverseBarcode)).editKey = KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0);
            design.get(getPropertyView(orderSalePayCard)).editKey = KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0);
            design.get(getPropertyView(orderSalePayCash)).editKey = KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0);


            // располагаем объекты на форме относительно друг друга
            if(!noOuters)
                design.addIntersection(design.getGroupObjectContainer(objOuter.groupTo), design.getGroupObjectContainer(objObligation.groupTo), DoNotIntersectSimplexConstraint.TOTHE_RIGHT);

            return design;
        }
    }

    private class CommitSaleCheckRetailNavigatorForm extends SaleRetailNavigatorForm {

        private CommitSaleCheckRetailNavigatorForm(NavigatorElement parent, int ID) {
            super(parent, ID, commitSaleCheckRetail, true);
        }
    }

    private class DistributeNavigatorForm extends DocumentInnerNavigatorForm {
        public DistributeNavigatorForm(NavigatorElement parent, int ID, boolean toAdd, CustomClass documentClass) {
            super(parent, ID, toAdd, documentClass, false);
        }

        @Override
        protected void fillExtraFilters(RegularFilterGroupNavigator filterGroup, boolean toAdd) {
            fillExtraLogisticsFilters(filterGroup, toAdd);
        }
    }

    private class DistributeShopNavigatorForm extends DocumentInnerNavigatorForm {
        public DistributeShopNavigatorForm(NavigatorElement parent, int ID, boolean toAdd) {
            super(parent, ID, toAdd, orderDistributeShop, false);
        }
    }

    private class DistributeWarehouseNavigatorForm extends DocumentInnerNavigatorForm {
        public DistributeWarehouseNavigatorForm(NavigatorElement parent, int ID, boolean toAdd) {
            super(parent, ID, toAdd, orderDistributeWarehouse, false);
        }
    }

    private class BalanceCheckNavigatorForm extends DocumentInnerNavigatorForm {
        public BalanceCheckNavigatorForm(NavigatorElement parent, int ID, boolean toAdd) {
            super(parent, ID, toAdd, balanceCheck, false);
        }
    }

    private class ReturnArticleNavigatorForm extends InnerNavigatorForm {
        final ObjectNavigator objInner;
        final ObjectNavigator objOuter;

        protected ReturnArticleNavigatorForm(NavigatorElement parent, int ID, boolean toAdd, CustomClass documentClass, CustomClass commitClass) {
            super(parent, ID, documentClass, toAdd, false);

            objInner = addSingleGroupObjectImplement(commitClass, "Документ к возврату", properties, baseGroup, true);

            addPropertyView(objInner, objDoc, properties, baseGroup, true, documentGroup, true);
            addPropertyView(objInner, objDoc, objArt, properties, baseGroup, true, documentGroup, true);
            addPropertyView(objInner, objArt, properties, baseGroup, true, documentPriceGroup, true);

            NotNullFilterNavigator documentFilter = new NotNullFilterNavigator(getPropertyImplement(returnInnerQuantity));
            NotNullFilterNavigator documentFreeFilter = new NotNullFilterNavigator(getPropertyImplement(returnFreeQuantity));
            if(fixFilters)
                addFixedFilter(new OrFilterNavigator(documentFilter, documentFreeFilter));
            RegularFilterGroupNavigator filterGroup = new RegularFilterGroupNavigator(IDShift(1));
            filterGroup.addFilter(new RegularFilterNavigator(IDShift(1),
                                  documentFilter,
                                  "Документ",
                                  KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0)), !toAdd);
            if(!fixFilters)
                filterGroup.addFilter(new RegularFilterNavigator(IDShift(1),
                                  documentFreeFilter,
                                  "Макс. кол-во",
                                  KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0)), toAdd);
            addRegularFilterGroup(filterGroup);

            objOuter = addSingleGroupObjectImplement(commitDelivery, "Партия", properties, baseGroup, true);

            addPropertyView(objInner, objOuter, objDoc, properties, baseGroup, true, documentGroup, true);
            addPropertyView(objInner, objOuter, objDoc, objArt, properties, baseGroup, true, documentGroup, true);
            addPropertyView(objInner, objOuter, properties, baseGroup, true);
            addPropertyView(objInner, objOuter, objArt, properties, baseGroup, true);

            NotNullFilterNavigator documentCommitFilter = new NotNullFilterNavigator(getPropertyImplement(returnInnerCommitQuantity));
            NotNullFilterNavigator documentCommitFreeFilter = new NotNullFilterNavigator(getPropertyImplement(returnInnerFreeQuantity));
            if(fixFilters)
                addFixedFilter(new OrFilterNavigator(documentCommitFilter, documentCommitFreeFilter));
            RegularFilterGroupNavigator filterOutGroup = new RegularFilterGroupNavigator(IDShift(1));
            filterOutGroup.addFilter(new RegularFilterNavigator(IDShift(1),
                                  documentCommitFilter,
                                  "Документ",
                                  KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0)), !toAdd);
            if(!fixFilters)
                filterOutGroup.addFilter(new RegularFilterNavigator(IDShift(1),
                                  documentCommitFreeFilter,
                                  "Макс. кол-во",
                                  KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0)), toAdd);
            addRegularFilterGroup(filterOutGroup);
        }

        @Override
        public DefaultFormView createDefaultRichDesign() {

            DefaultFormView form = super.createDefaultRichDesign();
            form.addIntersection(form.getGroupObjectContainer(objInner.groupTo), form.getGroupObjectContainer(objOuter.groupTo), DoNotIntersectSimplexConstraint.TOTHE_RIGHT);
            return form;
        }
    }

    private class ReturnSaleWholeArticleNavigatorForm extends ReturnArticleNavigatorForm {
        private ReturnSaleWholeArticleNavigatorForm(NavigatorElement parent, boolean toAdd, int ID) {
            super(parent, ID, toAdd, returnSaleWhole, commitSaleWhole);
        }
    }

    private class ReturnSaleInvoiceRetailArticleNavigatorForm extends ReturnArticleNavigatorForm {
        private ReturnSaleInvoiceRetailArticleNavigatorForm(NavigatorElement parent, boolean toAdd, int ID) {
            super(parent, ID, toAdd, returnSaleInvoiceRetail, commitSaleInvoiceRetail);
        }
    }

    private class ReturnSaleCheckRetailArticleNavigatorForm extends ReturnArticleNavigatorForm {
        private ReturnSaleCheckRetailArticleNavigatorForm(NavigatorElement parent, boolean toAdd, int ID) {
            super(parent, ID, toAdd, returnSaleCheckRetail, commitSaleCheckRetail);
        }
    }

    private class SupplierArticleNavigatorForm extends NavigatorForm {
        protected SupplierArticleNavigatorForm(NavigatorElement parent, int ID) {
            super(parent, ID, "Ассортимент поставщиков");

            ObjectNavigator objSupplier = addSingleGroupObjectImplement(supplier, "Поставщик", properties, allGroup, true);
            ObjectNavigator objArt = addSingleGroupObjectImplement(article, "Товар", properties, allGroup, true);

            addPropertyView(objSupplier, objArt, properties, allGroup, true);

            addFixedFilter(new CompareFilterNavigator(addPropertyObjectImplement(articleSupplier,objArt),Compare.EQUALS,objSupplier));
        }
    }

    private class StoreArticleNavigatorForm extends BarcodeNavigatorForm {
        private ObjectNavigator objArt;

        protected StoreArticleNavigatorForm(NavigatorElement parent, int ID) {
            super(parent, ID, "Остатки по складу");

            ObjectNavigator objStore = addSingleGroupObjectImplement(store, "Склад", properties, allGroup, true);
            objArt = addSingleGroupObjectImplement(article, "Товар", properties, allGroup, true);
            ObjectNavigator objOuter = addSingleGroupObjectImplement(commitDelivery, "Партия", properties, allGroup, true);

            addPropertyView(objStore, objArt, properties, allGroup, true);
            addPropertyView(objStore, objOuter, properties, allGroup, true);
            addPropertyView(objOuter, objArt, properties, baseGroup, true);
            addPropertyView(objStore, objOuter, objArt, properties, allGroup, true);
        }

        @Override
        public DefaultFormView createDefaultRichDesign() {

            DefaultFormView form = super.createDefaultRichDesign();
            form.get(objArt.groupTo).gridView.constraints.fillVertical = 3;
            return form;
        }
    }

    private class FormatArticleNavigatorForm extends BarcodeNavigatorForm {
        protected FormatArticleNavigatorForm(NavigatorElement parent, int ID) {
            super(parent, ID, "Остатки по форматам");

            ObjectNavigator objFormat = addSingleGroupObjectImplement(format, "Формат", properties, allGroup, true);
            ObjectNavigator objArt = addSingleGroupObjectImplement(article, "Товар", properties, allGroup, true);

            addPropertyView(objFormat, objArt, properties, allGroup, true);

            addBarcode(article, articleFormatToSell);
        }
    }

    private class DocumentRevalueNavigatorForm extends ArticleNoCheckNavigatorForm {

        protected PropertyObjectNavigator getCommitedQuantity() {
            return getPropertyImplement(documentRevalued);
        }

        protected DocumentRevalueNavigatorForm(NavigatorElement parent, boolean toAdd, int ID) {
            super(parent, ID, documentRevalue, toAdd, true);
        }
    }

    private class DocumentNDSNavigatorForm extends ArticleNoCheckNavigatorForm {

        protected PropertyObjectNavigator getCommitedQuantity() {
            return getPropertyImplement(NDS);
        }

        @Override
        protected void fillExtraFilters(RegularFilterGroupNavigator filterGroup, boolean toAdd) {
            filterGroup.addFilter(new RegularFilterNavigator(IDShift(1),
                                  new NotFilterNavigator(new NotNullFilterNavigator(getPropertyImplement(currentNDS))),
                                  "Без НДС",
                                  KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0)), toAdd);
        }

        protected DocumentNDSNavigatorForm(NavigatorElement parent, boolean toAdd, int ID) {
            super(parent, ID, documentNDS, toAdd);

            addHintsNoUpdate(currentNDSDoc);
            addHintsNoUpdate(currentNDSDate);
        }
    }

    private class IncomePriceNavigatorForm extends NavigatorForm {

        protected IncomePriceNavigatorForm(NavigatorElement parent, int ID) {
            super(parent, ID, "Реестр цен", true);

            ObjectNavigator objDoc = addSingleGroupObjectImplement(commitWholeShopInc, "Документ", properties, baseGroup, true);
            objDoc.groupTo.initClassView = ClassViewType.PANEL;
            ObjectNavigator objArt = addSingleGroupObjectImplement(article, "Товар", properties, baseGroup, true);

            addPropertyView(objDoc, objArt, properties, articleQuantity, shopPrice);

            addFixedFilter(new NotNullFilterNavigator(getPropertyImplement(shopPrice)));

            addFAProp(documentPriceGroup, "Реестр цен", this, objDoc);
        }
    }

    private class RevalueActNavigatorForm extends NavigatorForm {

        protected RevalueActNavigatorForm(NavigatorElement parent, int ID) {
            super(parent, ID, "Акт переоценки", true);

            ObjectNavigator objDoc = addSingleGroupObjectImplement(documentShopPrice, "Документ", properties, baseGroup, true);
            objDoc.groupTo.initClassView = ClassViewType.PANEL;
            ObjectNavigator objArt = addSingleGroupObjectImplement(article, "Товар", properties, baseGroup, true);

            addPropertyView(objDoc, objArt, properties, articleQuantity, shopPrice, prevPrice, revalBalance);

            addFixedFilter(new CompareFilterNavigator(getPropertyImplement(shopPrice), Compare.NOT_EQUALS, getPropertyImplement(prevPrice)));

            addFAProp(documentPriceGroup, "Акт переоценки", this, objDoc);
        }
    }

    private class ActionNavigatorForm extends BarcodeNavigatorForm {
        protected ActionNavigatorForm(NavigatorElement parent, int ID) {
            super(parent, ID, "Акции");

            ObjectNavigator objAction = addSingleGroupObjectImplement(action, "Акция", properties, allGroup, true);
            ObjectNavigator objArt = addSingleGroupObjectImplement(article, "Товар", properties, allGroup, true);

            addPropertyView(objAction, objArt, properties, allGroup, true);

            addBarcode(article, inAction);
        }
    }

    private class PricersNavigatorForm extends NavigatorForm {

        protected PricersNavigatorForm(NavigatorElement parent, int ID) {
            super(parent, ID, "Ценники", true);

            ObjectNavigator objDoc = addSingleGroupObjectImplement(documentShopPrice, "Документ", properties, baseGroup, true);
            objDoc.groupTo.initClassView = ClassViewType.PANEL;
            ObjectNavigator objArt = addSingleGroupObjectImplement(article, "Товар", properties, baseGroup, true);

            addPropertyView(objDoc, objArt, properties, shopPrice);

            addFixedFilter(new NotNullFilterNavigator(getPropertyImplement(shopPrice)));
            addFixedFilter(new NotFilterNavigator(new CompareFilterNavigator(getPropertyImplement(shopPrice), Compare.EQUALS, addPropertyObjectImplement(prevPrice,objDoc,objArt))));

            addFAProp(documentPriceGroup, "Ценники", this, objDoc);
        }
    }

    private class SaleDocumentObligationNavigatorForm extends DocumentNavigatorForm {
        protected SaleDocumentObligationNavigatorForm(NavigatorElement parent, int ID, boolean toAdd) {
            super(parent, ID, saleDocumentObligation, toAdd);

            ObjectNavigator objObligation = addSingleGroupObjectImplement(giftObligation, "Пода", properties, allGroup, true);

            addPropertyView(objDoc, objObligation, properties, allGroup, true);

            addFixedFilter(new CompareFilterNavigator(addPropertyObjectImplement(obligationSaleDocument,objObligation),Compare.EQUALS,objDoc));
        }
    }

    protected void initAuthentication() throws ClassNotFoundException, SQLException, IllegalAccessException, InstantiationException {
        User user1 = addUser("user1");
    }
}
