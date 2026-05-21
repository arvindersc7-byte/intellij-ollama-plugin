package com;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ExecutionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.messages.MessageBusConnection;

import javax.swing.*;
import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        JPanel panel = new JPanel(new BorderLayout());
        JTextArea logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);

        // Add Clean button at the bottom
        JButton clearButton = new JButton("Clean");
        clearButton.addActionListener(e -> logArea.setText(""));

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.add(clearButton);

        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, "Arvinder Log Watcher", false);
        toolWindow.getContentManager().addContent(content);

        // Attach to already running processes
        attachConsoleListeners(project, logArea);

        // Subscribe to new executions
        MessageBusConnection connection = project.getMessageBus().connect();
        connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
            @Override
            public void processStarted(String executorId, ExecutionEnvironment env, ProcessHandler handler) {
                if (handler != null) {
                    attachHandler(handler, logArea, project);
                }
            }
        });
    }

    private void attachConsoleListeners(Project project, JTextArea logArea) {
        ProcessHandler[] handlers = ExecutionManager.getInstance(project).getRunningProcesses();
        for (ProcessHandler handler : handlers) {
            if (handler != null) {
                attachHandler(handler, logArea, project);
            }
        }
    }

    private void attachHandler(ProcessHandler handler, JTextArea logArea, Project project) {
        handler.addProcessListener(new ProcessAdapter() {
            @Override
            public void onTextAvailable(ProcessEvent event, com.intellij.openapi.util.Key outputType) {
                String text = event.getText();

                ApplicationManager.getApplication().invokeLater(() -> {
                    // Check both stdout and stderr for exceptions
                    if (isErrorLine(text) || isStackTraceLine(text)) {
                        logArea.append(text);

                        // Show popup dialog in IntelliJ center when new exception header is detected
                        if (isErrorLine(text)) {
                            String lineNumber = extractLineNumber(text);
                            String message = (lineNumber != null)
                                    ? text + "\nCulprit line: " + lineNumber
                                    : text;

                            Messages.showErrorDialog(project,
                                    message,
                                    "Exception Detected");
                        }
                    }
                });
            }
        });
    }

    /**
     * Detects the start of an exception or error message.
     */
    private boolean isErrorLine(String line) {
        return line.contains("Exception")   // ArithmeticException, NullPointerException, etc.
                || line.contains("Error")  // AssertionError, OutOfMemoryError
                || line.contains("Caused by")
                || line.contains("Failure")
                || line.contains("SQLException")
                || line.contains("DataAccessException")
                || line.contains("JpaSystemException")
                || line.contains("KafkaException")
                || line.contains("SerializationException");
    }

    /**
     * Detects stack trace lines (culprit code lines).
     */
    private boolean isStackTraceLine(String line) {
        return line.trim().startsWith("at ");
    }

    /**
     * Extracts line number from a stack trace line like "(MyClass.java:42)".
     */
    private String extractLineNumber(String line) {
        Matcher matcher = Pattern.compile("\\((.*\\.java):(\\d+)\\)").matcher(line);
        if (matcher.find()) {
            return matcher.group(2); // line number
        }
        return null;
    }
}
