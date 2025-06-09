package org.lebastudios.theroundtableplugins.accounting;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Callback;
import org.lebastudios.theroundtable.components.DateRangePicker;
import org.lebastudios.theroundtable.controllers.PaneController;
import org.lebastudios.theroundtable.database.Database;
import org.lebastudios.theroundtable.locale.LocaleManager;
import org.lebastudios.theroundtable.maths.BigDecimalOperations;
import org.lebastudios.theroundtableplugins.cr.entities.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class MainPaneController extends PaneController<MainPaneController>
{
    @FXML public DateRangePicker dateRangePicker;
    @FXML public ChoiceBox<String> employeeChoiceBox;
    @FXML public RadioButton cashRadioButton;
    @FXML public RadioButton creditRadioButton;

    @FXML public TableView<TransactionTableItem> tableView;
    @FXML public TableColumn<TransactionTableItem, LocalDateTime> dateColumn;
    @FXML public TableColumn<TransactionTableItem, String> employeeColumn;
    @FXML public TableColumn<TransactionTableItem, String> conceptColumn;
    @FXML public TableColumn<TransactionTableItem, Transaction.PaymentMethod> methodColumn;
    @FXML public TableColumn<TransactionTableItem, BigDecimal> qtyColumn;
    @FXML public TableColumn<TransactionTableItem, BigDecimal> totalColumn;

    private List<TransactionTableItem> foundItems = new ArrayList<>();

    @Override
    protected void initialize()
    {
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        List<String> employees = Database.getInstance().connectQuery(session ->
                {
                    return session.createQuery("select a.name from Account a", String.class)
                            .getResultList();
                }
        );

        employeeChoiceBox.getItems().clear();
        employeeChoiceBox.getItems().add("All");
        employeeChoiceBox.getItems().addAll(employees);
        employeeChoiceBox.getSelectionModel().select(0);

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

        employeeColumn.setCellValueFactory(cellData -> cellData.getValue().employee);
        conceptColumn.setCellValueFactory(cellData -> cellData.getValue().concept);

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

        qtyColumn.setCellValueFactory(cellData -> cellData.getValue().amount);
        qtyColumn.setCellFactory(bigDecimalCell);

        totalColumn.setCellValueFactory(cellData -> cellData.getValue().totalInCash);
        totalColumn.setCellFactory(bigDecimalCell);
        
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

        dateRangePicker.setOnDateChange((from, to) -> search(from.atStartOfDay(), to.atTime(LocalTime.MAX)));
        search(
                dateRangePicker.getStartDate().getValue().atStartOfDay(),
                dateRangePicker.getEndDate().getValue().atTime(LocalTime.MAX)
        );
    }

    private void search(LocalDateTime from, LocalDateTime to)
    {
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
                    if (!(cashRadioButton.isSelected() && item.method.get() == Transaction.PaymentMethod.CASH
                            || creditRadioButton.isSelected() && item.method.get() == Transaction.PaymentMethod.CARD))
                    {
                        return false;
                    }

                    if (employeeChoiceBox.getSelectionModel().isSelected(0) 
                            || employeeChoiceBox.getSelectionModel().getSelectedItem().equals(item.employee.get()))
                    {
                        return true;
                    }

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
