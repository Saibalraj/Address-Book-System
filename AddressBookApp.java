import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;

public class AddressBookApp {

    private static final Path DATA_FILE = Paths.get("contacts.csv");
    private final List<Contact> contacts = new ArrayList<>();

    private JFrame frame;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField nameField, phoneField, emailField, addressField, searchField;
    private JButton addBtn, updateBtn, deleteBtn, importBtn, exportBtn, clearBtn;

    private int selectedIndex = -1;
    private static final String[] COLS = {"ID", "Name", "Phone", "Email", "Address"};

    private static class Contact {
        String id;
        String name;
        String phone;
        String email;
        String address;

        Contact(String id, String name, String phone, String email, String address) {
            this.id = id;
            this.name = name;
            this.phone = phone;
            this.email = email;
            this.address = address;
        }
    }

    public AddressBookApp() {
        SwingUtilities.invokeLater(() -> {
            buildUI();
            loadFromFile();
            refreshTable();
            frame.setVisible(true);
        });
    }

    private void buildUI() {
        frame = new JFrame("Address Book");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 560);
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        frame.setContentPane(root);

        JPanel top = new JPanel(new BorderLayout(8, 8));
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.add(new JLabel("Search:"));
        searchField = new JTextField(30);
        searchPanel.add(searchField);
        top.add(searchPanel, BorderLayout.WEST);

        JPanel fileBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        importBtn = new JButton("Import CSV");
        exportBtn = new JButton("Export CSV");
        fileBtns.add(importBtn);
        fileBtns.add(exportBtn);
        top.add(fileBtns, BorderLayout.EAST);

        root.add(top, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableColumnModel tcm = table.getColumnModel();
        tcm.getColumn(0).setMinWidth(0);
        tcm.getColumn(0).setMaxWidth(0);
        tcm.getColumn(0).setWidth(0);

        root.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createTitledBorder("Contact Details"));
        form.add(Box.createVerticalStrut(6));

        nameField = labeledTextField(form, "Name:");
        phoneField = labeledTextField(form, "Phone:");
        emailField = labeledTextField(form, "Email:");
        addressField = labeledTextField(form, "Address:");

        form.add(Box.createVerticalStrut(8));
        addBtn = new JButton("Add Contact");
        updateBtn = new JButton("Update Selected");
        deleteBtn = new JButton("Delete Selected");
        clearBtn = new JButton("Clear Form");

        for (JButton b : new JButton[]{addBtn, updateBtn, deleteBtn, clearBtn}) {
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            form.add(b);
            form.add(Box.createVerticalStrut(6));
        }

        root.add(form, BorderLayout.EAST);

        addBtn.addActionListener(e -> addContact());
        updateBtn.addActionListener(e -> updateContact());
        deleteBtn.addActionListener(e -> deleteContact());
        clearBtn.addActionListener(e -> clearForm());
        importBtn.addActionListener(e -> importCsv());
        exportBtn.addActionListener(e -> exportCsv());

        table.getSelectionModel().addListSelectionListener(ev -> {
            if (!ev.getValueIsAdjusting()) onTableSelection();
        });

        searchField.getDocument().addDocumentListener(new SimpleDocListener(this::refreshTableFiltered));

        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) onTableSelection();
            }
        });

        updateBtn.setEnabled(false);
        deleteBtn.setEnabled(false);
    }

    private JTextField labeledTextField(JPanel container, String label) {
        JPanel p = new JPanel(new BorderLayout(6, 6));
        JLabel l = new JLabel(label);
        JTextField tf = new JTextField();
        p.add(l, BorderLayout.WEST);
        p.add(tf, BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(350, 40));
        container.add(p);
        container.add(Box.createVerticalStrut(6));
        return tf;
    }

    // ========================
    //   CRUD OPERATIONS
    // ========================

    private void addContact() {
        String name = nameField.getText().trim();
        String phone = phoneField.getText().trim();
        String email = emailField.getText().trim();
        String address = addressField.getText().trim();

        if (name.isEmpty() && phone.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Please enter at least a name or phone.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String id = generateId();
        contacts.add(new Contact(id, name, phone, email, address));
        saveToFile();
        refreshTable();
        clearForm();
        JOptionPane.showMessageDialog(frame, "Contact added.");
    }

    private void updateContact() {
        if (selectedIndex < 0 || selectedIndex >= contacts.size()) return;
        Contact c = contacts.get(selectedIndex);
        c.name = nameField.getText().trim();
        c.phone = phoneField.getText().trim();
        c.email = emailField.getText().trim();
        c.address = addressField.getText().trim();
        saveToFile();
        refreshTable();
        clearForm();
        JOptionPane.showMessageDialog(frame, "Contact updated.");
    }

    private void deleteContact() {
        if (selectedIndex < 0 || selectedIndex >= contacts.size()) return;
        Contact c = contacts.get(selectedIndex);
        int yn = JOptionPane.showConfirmDialog(frame, "Delete contact \"" + c.name + "\" ?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (yn == JOptionPane.YES_OPTION) {
            contacts.remove(selectedIndex);
            saveToFile();
            refreshTable();
            clearForm();
        }
    }

    private void clearForm() {
        table.clearSelection();
        selectedIndex = -1;
        nameField.setText("");
        phoneField.setText("");
        emailField.setText("");
        addressField.setText("");
        updateBtn.setEnabled(false);
        deleteBtn.setEnabled(false);
        addBtn.setEnabled(true);
    }

    private void onTableSelection() {
        int sel = table.getSelectedRow();
        if (sel == -1) {
            clearForm();
            return;
        }

        int modelIndex = table.convertRowIndexToModel(sel);
        String id = (String) tableModel.getValueAt(modelIndex, 0);

        selectedIndex = -1;
        for (int i = 0; i < contacts.size(); i++) {
            if (contacts.get(i).id.equals(id)) {
                selectedIndex = i;
                break;
            }
        }

        if (selectedIndex >= 0) {
            Contact c = contacts.get(selectedIndex);
            nameField.setText(c.name);
            phoneField.setText(c.phone);
            emailField.setText(c.email);
            addressField.setText(c.address);
            updateBtn.setEnabled(true);
            deleteBtn.setEnabled(true);
            addBtn.setEnabled(false);
        }
    }

    // ========================
    //   TABLE REFRESH
    // ========================

    private void refreshTable() {
        refreshTableFiltered();
    }

    private void refreshTableFiltered() {
        String q = searchField.getText().trim().toLowerCase();

        List<Contact> visible =
                q.isEmpty() ? new ArrayList<>(contacts)
                        : contacts.stream().filter(c ->
                        (c.name != null && c.name.toLowerCase().contains(q)) ||
                        (c.phone != null && c.phone.toLowerCase().contains(q)) ||
                        (c.email != null && c.email.toLowerCase().contains(q)) ||
                        (c.address != null && c.address.toLowerCase().contains(q))
        ).collect(Collectors.toList());

        tableModel.setRowCount(0);
        for (Contact c : visible) {
            tableModel.addRow(new Object[]{c.id, c.name, c.phone, c.email, c.address});
        }
    }

    // ========================
    //    FILE OPERATIONS
    // ========================

    private void saveToFile() {
        try {
            List<String> lines = new ArrayList<>();
            lines.add("id,name,phone,email,address");
            for (Contact c : contacts) {
                lines.add(csvEscape(c.id) + "," + csvEscape(c.name) + "," +
                        csvEscape(c.phone) + "," + csvEscape(c.email) + "," + csvEscape(c.address));
            }
            Files.write(DATA_FILE, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Failed to save: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void loadFromFile() {
        contacts.clear();
        if (!Files.exists(DATA_FILE)) return;
        try {
            List<String> lines = Files.readAllLines(DATA_FILE, StandardCharsets.UTF_8);
            boolean first = true;
            for (String ln : lines) {
                if (first && ln.toLowerCase().startsWith("id,")) { first = false; continue; }
                first = false;
                String[] parts = csvSplit(ln, 5);
                if (parts.length < 5) continue;
                contacts.add(new Contact(parts[0], parts[1], parts[2], parts[3], parts[4]));
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Failed to load contacts: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void importCsv() {
        JFileChooser fc = new JFileChooser();
        int res = fc.showOpenDialog(frame);
        if (res != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();

        try {
            List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            boolean first = true;
            int added = 0;

            for (String ln : lines) {
                if (first && ln.toLowerCase().startsWith("id,")) { first = false; continue; }
                first = false;

                String[] parts = csvSplit(ln, 5);
                if (parts.length < 5) continue;

                String id = parts[0].isEmpty() ? generateId() : parts[0];

                // FIXED: no mutation inside lambda
                while (idExists(id)) {
                    id = generateId();
                }

                contacts.add(new Contact(id, parts[1], parts[2], parts[3], parts[4]));
                added++;
            }

            if (added > 0) {
                saveToFile();
                refreshTable();
                JOptionPane.showMessageDialog(frame, "Imported " + added + " contacts.");
            } else {
                JOptionPane.showMessageDialog(frame, "No contacts found in file.");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Import failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void exportCsv() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("contacts_export.csv"));
        int res = fc.showSaveDialog(frame);
        if (res != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();

        try {
            List<String> lines = new ArrayList<>();
            lines.add("id,name,phone,email,address");
            for (Contact c : contacts) {
                lines.add(csvEscape(c.id) + "," + csvEscape(c.name) + "," +
                        csvEscape(c.phone) + "," + csvEscape(c.email) + "," + csvEscape(c.address));
            }
            Files.write(f.toPath(), lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            JOptionPane.showMessageDialog(frame, "Exported to " + f.getAbsolutePath());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Export failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    // ========================
    //  UTILITIES
    // ========================

    private boolean idExists(String id) {
        for (Contact c : contacts) {
            if (c.id.equals(id)) return true;
        }
        return false;
    }

    private String generateId() {
        return "C" + System.currentTimeMillis() + (int)(Math.random() * 1000);
    }

    private static String csvEscape(String s) {
        if (s == null) return "";
        String t = s.replace("\"", "\"\"");
        if (t.contains(",") || t.contains("\"") || t.contains("\n") || t.contains("\r")) {
            return "\"" + t + "\"";
        }
        return t;
    }

    private static String[] csvSplit(String line, int expectedColumns) {
        List<String> parts = new ArrayList<>(expectedColumns);
        if (line == null || line.isEmpty()) {
            for (int i = 0; i < expectedColumns; i++) parts.add("");
            return parts.toArray(new String[0]);
        }
        int len = line.length();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < len; i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < len && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        parts.add(cur.toString());
        while (parts.size() < expectedColumns) parts.add("");
        return parts.toArray(new String[0]);
    }

    private static class SimpleDocListener implements DocumentListener {
        private final Runnable r;
        SimpleDocListener(Runnable r) { this.r = r; }
        public void insertUpdate(DocumentEvent e) { r.run(); }
        public void removeUpdate(DocumentEvent e) { r.run(); }
        public void changedUpdate(DocumentEvent e) { r.run(); }
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        new AddressBookApp();
    }
}
