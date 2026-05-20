package com;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.TimeUnit;

public class MyToolWindowFactory implements ToolWindowFactory {

    private static final Logger LOG =
            Logger.getInstance(MyToolWindowFactory.class);

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {

        LOG.info("TOOL WINDOW LOADED");

        // =========================================
        // MAIN PANEL
        // =========================================
        JPanel panel = new JPanel(new BorderLayout());

        // =========================================
        // TOP PANEL
        // =========================================
        JPanel topPanel = new JPanel(new BorderLayout());

        JTextField inputField = new JTextField();
        JButton generateButton = new JButton("Generate");

        topPanel.add(inputField, BorderLayout.CENTER);
        topPanel.add(generateButton, BorderLayout.EAST);

        // =========================================
        // OUTPUT AREA
        // =========================================
        JTextArea outputArea = new JTextArea();

        outputArea.setEditable(false);
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(outputArea);

        // =========================================
        // ADD COMPONENTS
        // =========================================
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        // =========================================
        // TOOL WINDOW CONTENT
        // =========================================
        ContentFactory contentFactory = ContentFactory.getInstance();

        Content content =
                contentFactory.createContent(
                        panel,
                        "",
                        false
                );

        toolWindow.getContentManager().addContent(content);

        // =========================================
        // BUTTON ACTION
        // =========================================
        generateButton.addActionListener(e -> {

            String userCommand =
                    inputField.getText().trim();

            if (userCommand.isEmpty()) {

                outputArea.append("Please enter prompt\n");
                return;
            }

            inputField.setText("");

            outputArea.append("\nGenerating...\n");

            // =========================================
            // RUN IN BACKGROUND THREAD
            // =========================================
            ApplicationManager.getApplication()
                    .executeOnPooledThread(() -> {

                        try {

                            OkHttpClient client =
                                    new OkHttpClient.Builder()
                                            .connectTimeout(
                                                    60,
                                                    TimeUnit.SECONDS
                                            )
                                            .readTimeout(
                                                    300,
                                                    TimeUnit.SECONDS
                                            )
                                            .writeTimeout(
                                                    300,
                                                    TimeUnit.SECONDS
                                            )
                                            .build();

                            // =========================================
                            // OLLAMA REQUEST JSON
                            // =========================================
                            String json =
                                    "{"
                                            + "\"model\":\"llama2\","
                                            + "\"prompt\":\"userCommand\","
                                            + "\"stream\":false"
                                            + " }";

                            LOG.info("Sending request to deepseek-coder");

                            Request request =
                                    new Request.Builder()
                                            .url("http://localhost:11434/api/generate")
                                            .post(
                                                    RequestBody.create(
                                                            json,
                                                            MediaType.parse(
                                                                    "application/json"
                                                            )
                                                    )
                                            )
                                            .build();

                            // =========================================
                            // EXECUTE API CALL
                            // =========================================
                            try (Response response =
                                         client.newCall(request).execute()) {

                                if (!response.isSuccessful()) {

                                    String errorBody =
                                            response.body() != null
                                                    ? response.body().string()
                                                    : "Unknown Error";

                                    ApplicationManager.getApplication()
                                            .invokeLater(() ->
                                                    outputArea.append(
                                                            "HTTP Error: "
                                                                    + errorBody
                                                                    + "\n"
                                                    )
                                            );

                                    return;
                                }

                                String body =
                                        response.body() != null
                                                ? response.body().string()
                                                : "";

                                LOG.info("OLLAMA RESPONSE:");
                                LOG.info(body);

                                String generatedCode =
                                        extractResponse(body);

                                // =========================================
                                // UPDATE UI
                                // =========================================
                                ApplicationManager.getApplication()
                                        .invokeLater(() -> {

                                            outputArea.append(
                                                    "\n========== GENERATED CODE ==========\n"
                                            );

                                            outputArea.append(
                                                    generatedCode
                                            );

                                            outputArea.append(
                                                    "\n====================================\n"
                                            );
                                        });

                                // =========================================
                                // CREATE JAVA FILE
                                // =========================================
                                ApplicationManager.getApplication()
                                        .invokeLater(() -> {

                                            WriteCommandAction
                                                    .runWriteCommandAction(
                                                            project,
                                                            () -> {

                                                                try {

                                                                    PsiDirectory dir =
                                                                            PsiManager
                                                                                    .getInstance(project)
                                                                                    .findDirectory(
                                                                                            project.getBaseDir()
                                                                                    );

                                                                    if (dir == null) {

                                                                        outputArea.append(
                                                                                "Project directory not found\n"
                                                                        );

                                                                        return;
                                                                    }

                                                                    String fileName =
                                                                            extractClassName(
                                                                                    generatedCode
                                                                            );

                                                                    PsiFile psiFile =
                                                                            PsiFileFactory
                                                                                    .getInstance(project)
                                                                                    .createFileFromText(
                                                                                            fileName,
                                                                                            JavaLanguage.INSTANCE,
                                                                                            generatedCode
                                                                                    );

                                                                    dir.add(psiFile);

                                                                    outputArea.append(
                                                                            "\nFile created: "
                                                                                    + fileName
                                                                                    + "\n"
                                                                    );

                                                                } catch (Exception ex) {

                                                                    LOG.error(ex);

                                                                    outputArea.append(
                                                                            "\nFile creation failed: "
                                                                                    + ex.getMessage()
                                                                                    + "\n"
                                                                    );
                                                                }
                                                            }
                                                    );
                                        });
                            }

                        } catch (Exception ex) {

                            LOG.error(ex);

                            ApplicationManager.getApplication()
                                    .invokeLater(() ->
                                            outputArea.append(
                                                    "\nError: "
                                                            + ex.getMessage()
                                                            + "\n"
                                            )
                                    );
                        }
                    });
        });
    }

    // =========================================
    // EXTRACT OLLAMA RESPONSE
    // =========================================
    private String extractResponse(String body) {

        if (body == null || body.isEmpty()) {
            return "No response";
        }

        try {

            int start =
                    body.indexOf("\"response\":\"");

            if (start == -1) {
                return body;
            }

            start += 12;

            int end =
                    body.indexOf("\",\"done\"", start);

            if (end == -1) {
                end = body.length();
            }

            return body.substring(start, end)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\t", "\t")
                    .replace("\\r", "");

        } catch (Exception ex) {

            LOG.error(ex);

            return body;
        }
    }

    // =========================================
    // ESCAPE JSON STRING
    // =========================================
    private String escapeJson(String text) {

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    // =========================================
    // EXTRACT CLASS NAME
    // =========================================
    private String extractClassName(String code) {

        try {

            String[] lines = code.split("\n");

            for (String line : lines) {

                line = line.trim();

                if (line.contains("class ")) {

                    String[] tokens =
                            line.split("\\s+");

                    for (int i = 0; i < tokens.length; i++) {

                        if ("class".equals(tokens[i])
                                && i + 1 < tokens.length) {

                            return tokens[i + 1] + ".java";
                        }
                    }
                }
            }

        } catch (Exception ex) {

            LOG.error(ex);
        }

        return "GeneratedFile.java";
    }
}