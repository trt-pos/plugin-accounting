package org.lebastudios.theroundtable.pluginaccounting;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Callback;
import org.lebastudios.theroundtable.controllers.PaneController;
import org.lebastudios.theroundtable.database.Database;
import org.lebastudios.theroundtable.locale.LocaleManager;
import org.lebastudios.theroundtable.maths.BigDecimalOperations;
import org.lebastudios.theroundtable.plugincashregister.entities.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class MainPaneController extends PaneController<MainPaneController>
{
    @FXML public DatePicker fromDatePicker;
    @FXML public DatePicker toDatePicker;
    @FXML public ChoiceBox<String> employeeChoiceBox;
    @FXML public RadioButton cashRadioButton;
    @FXML public RadioButton creditRadioButton;
    @FXML public TableView<TransactionTableItem> tableView;

    private List<TransactionTableItem> foundItems = new ArrayList<>();

    @Override
    protected void initialize()
    {
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        fromDatePicker.setValue(LocalDate.now());
        toDatePicker.setValue(LocalDate.now());

        List<String> employees = Database.getInstance().connectQuery(session ->
                {
                    return session.createQuery("select a.name from Account a", String.class)
                            .getResultList();
                }
        );

        employeeChoiceBox.getItems().clear();
        employeeChoiceBox.getItems().add("All");
        employeeChoiceBox.getItems().addAll(employees);

        final var cols = tableView.getColumns();

        TableColumn<TransactionTableItem, LocalDateTime> dateColumn =
                (TableColumn<TransactionTableItem, LocalDateTime>) cols.get(0);
        dateColumn.setCellValueFactory(cellData -> cellData.getValue().time);
        dateColumn.setCellFactory(_ -> new TableCell<>()
        {
            private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

            @Override
            protected void updateItem(LocalDateTime item, boolean empty)
            {
                super.updateItem(item, empty);
                if (empty || item == null)
                {
                    setText(null);
                }
                else
                {
                    setText(item.format(formatter));
                }
            }
        });

        ((TableColumn<TransactionTableItem, String>) cols.get(1)).setCellValueFactory(
                cellData -> cellData.getValue().employee);
        ((TableColumn<TransactionTableItem, String>) cols.get(2)).setCellValueFactory(
                cellData -> cellData.getValue().concept);

        final var methodColumn =
                (TableColumn<TransactionTableItem, Transaction.PaymentMethod>) cols.get(3);
        methodColumn.setCellValueFactory(cellData -> cellData.getValue().method);
        methodColumn.setCellFactory(_ -> new TableCell<>()
        {
            @Override
            protected void updateItem(Transaction.PaymentMethod item, boolean empty)
            {
                super.updateItem(item, empty);
                if (empty || item == null)
                {
                    setText(null);
                }
                else
                {
                    setText(item.translate());
                }
            }
        });

        Callback<TableColumn<TransactionTableItem, BigDecimal>,
                TableCell<TransactionTableItem, BigDecimal>
                > bigDecimalCell = new Callback<>()
        {
            @Override
            public TableCell<TransactionTableItem, BigDecimal> call(
                    TableColumn<TransactionTableItem, BigDecimal> param)
            {
                return new TableCell<>()
                {
                    @Override
                    protected void updateItem(BigDecimal item, boolean empty)
                    {
                        super.updateItem(item, empty);
                        if (empty || item == null)
                        {
                            setText(null);
                            return;
                        }

                        if (item.compareTo(BigDecimal.ZERO) == 0)
                        {
                            setText("---- " + LocaleManager.getInstance().getActualCurrency().symbol());
                            return;
                        }

                        setText(BigDecimalOperations.toCurrencyString(item));
                    }
                };
            }
        };

        final var amountColumn = (TableColumn<TransactionTableItem, BigDecimal>) cols.get(4);
        amountColumn.setCellValueFactory(cellData -> cellData.getValue().amount);
        amountColumn.setCellFactory(bigDecimalCell);

        final var totalColumn = (TableColumn<TransactionTableItem, BigDecimal>) cols.get(5);
        totalColumn.setCellValueFactory(cellData -> cellData.getValue().totalInCash);
        totalColumn.setCellFactory(bigDecimalCell);

        fromDatePicker.valueProperty().addListener((_, _, newVal) ->
        {
            if (newVal == null)
            {
                fromDatePicker.setValue(LocalDate.now());
                return;
            }

            if (newVal.isAfter(toDatePicker.getValue()))
            {
                toDatePicker.setValue(newVal);
            }

            search();
        });

        toDatePicker.valueProperty().addListener((_, _, newVal) ->
        {
            if (newVal == null)
            {
                toDatePicker.setValue(LocalDate.now());
                return;
            }

            if (newVal.isBefore(fromDatePicker.getValue()))
            {
                fromDatePicker.setValue(newVal);
            }

            search();
        });

        cashRadioButton.selectedProperty().addListener(_ -> populate());
        creditRadioButton.selectedProperty().addListener(_ -> populate());

        employeeChoiceBox.getSelectionModel().selectedItemProperty().addListener((_, _, newVal) ->
        {
            if (newVal == null)
            {
                employeeChoiceBox.getSelectionModel().select(0);
                return;
            }

            populate();
        });

        search();
    }

    private void search()
    {
        LocalDateTime from = fromDatePicker.getValue().atStartOfDay();
        LocalDateTime to = toDatePicker.getValue().atTime(LocalTime.MAX);

        foundItems = Database.getInstance().connectQuery(session ->
        {
            return session.createQuery(
                            "from Transaction t " +
                                    "where t.date >= :from and t.date <= :to " +
                                    "order by t.date desc ",
                            Transaction.class
                    )
                    .setParameter("from", from)
                    .setParameter("to", to)
                    .getResultList()
                    .stream()
                    .map(TransactionTableItem::new)
                    .toList();
        });

        populate();
    }

    private void populate()
    {
        List<TransactionTableItem> items = foundItems.parallelStream()
                .filter(item ->
                {
                    if (cashRadioButton.isSelected() && item.method.get() == Transaction.PaymentMethod.CASH) return true;
                    if (creditRadioButton.isSelected() && item.method.get() == Transaction.PaymentMethod.CARD) return true;

                    if (employeeChoiceBox.getSelectionModel().isSelected(0)) return true;
                    if (employeeChoiceBox.getSelectionModel().getSelectedItem().equals(item.employee.get()))
                    {return true;}

                    return false;
                })
                .toList();

        tableView.getItems().setAll(items);
    }

    public record TransactionTableItem(
            SimpleObjectProperty<LocalDateTime> time,
            SimpleStringProperty employee,
            SimpleStringProperty concept,
            SimpleObjectProperty<Transaction.PaymentMethod> method,
            SimpleObjectProperty<BigDecimal> amount,
            SimpleObjectProperty<BigDecimal> totalInCash
    )
    {
        public TransactionTableItem(
                LocalDateTime time, String employee, String concept, Transaction.PaymentMethod method,
                BigDecimal amount, BigDecimal totalInCash
        )
        {
            this(
                    new SimpleObjectProperty<>(time), new SimpleStringProperty(employee),
                    new SimpleStringProperty(concept),
                    new SimpleObjectProperty<>(method), new SimpleObjectProperty<>(amount),
                    new SimpleObjectProperty<>(totalInCash)
            );
        }

        public TransactionTableItem(Transaction transaction)
        {
            this(
                    transaction.getDate(),
                    transaction.getReceipt() == null ? "Unknown" : transaction.getAccount().getName(),
                    transaction.getDescription(),
                    transaction.getReceipt() == null ? Transaction.PaymentMethod.CASH : transaction.getMethod(),
                    transaction.getAmount(),
                    transaction.getTotalCash()
            );
        }
    }
}
