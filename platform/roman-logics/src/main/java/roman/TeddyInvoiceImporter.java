package roman;

import platform.server.classes.DateClass;
import platform.server.integration.ImportField;
import platform.server.integration.ImportInputTable;
import platform.server.integration.SingleSheetImporter;

import java.sql.Date;
import java.text.ParseException;

/**
 * User: DAle
 * Date: 25.02.11
 * Time: 15:51
 */

public class TeddyInvoiceImporter extends SingleSheetImporter {
    private static final int BARCODENUMBER = L, LAST_COLUMN = AJ;

    public TeddyInvoiceImporter(ImportInputTable inputTable, Object... fields) {
        super(inputTable, fields);
    }

    @Override
    protected boolean isCorrectRow(int rowNum) {
        return inputTable.getCellString(rowNum, BARCODENUMBER).trim().matches("^(\\d{13}|\\d{12}|\\d{8})$");
    }

    @Override
    protected String getCellString(ImportField field, int row, int column) throws ParseException {
        if (column <= LAST_COLUMN) {
            return super.getCellString(field, row, column);
        } else if (column == LAST_COLUMN + 1) {
            return String.valueOf(currentRow + 1);
        } else {
            return "";
        }
    }

    @Override
    protected String transformValue(int row, int column, int part, String value) {
        value = value.trim();

        switch (column) {
            case G:
                if (value.length() >= 10) {
                    Date sDate = new Date(Integer.parseInt(value.substring(0, 4)) - 1900, Integer.parseInt(value.substring(5, 7)) - 1, Integer.parseInt(value.substring(8, 10)));
                    return DateClass.format(sDate);
                }
            case W:
                return String.valueOf(Double.valueOf(value) / 100);
            case X:
                return String.valueOf(Double.valueOf(value) / 1000000);
            case AA:
                return String.valueOf(Double.valueOf(value) / 100);
            default:
                return value;
        }
    }
}
