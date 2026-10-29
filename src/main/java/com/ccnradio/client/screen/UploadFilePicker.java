package com.ccnradio.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Opens the player's operating system file browser so an audio file can be picked without
 * typing its path.
 *
 * <p>AWT/Swing windows must be created on the AWT event dispatch thread, which is not
 * Minecraft's render thread, so the browser runs on its own daemon thread and reports back
 * through {@link Minecraft#execute(Runnable)}. The Swing chooser is tried first because a
 * plain {@link FileDialog} is not reliably raised above a fullscreen game window; the native
 * dialog is kept as a fallback. If neither window can be created (for example on a JVM that
 * runs the game headless) the failure key is reported so the screen can tell the player to
 * paste a path instead.</p>
 */
public final class UploadFilePicker {
    private static final AtomicBoolean OPEN = new AtomicBoolean();
    private static volatile File lastDirectory;

    private UploadFilePicker() {
    }

    /**
     * @param onSelected invoked on the client thread with the chosen file; never null
     * @param onError    invoked on the client thread with a translation key when no window
     *                   could be opened
     */
    public static void pick(Consumer<Path> onSelected, Consumer<String> onError) {
        if (!OPEN.compareAndSet(false, true)) {
            return;
        }
        String title = Component.translatable("gui.ccnradio.browse_title").getString();
        String filterLabel = Component.translatable("gui.ccnradio.filter_audio").getString();
        Thread thread = new Thread(() -> {
            Path selected = null;
            String errorKey = null;
            try {
                selected = chooseWithSwing(title, filterLabel);
            } catch (Throwable swingFailure) {
                try {
                    selected = chooseWithAwt(title);
                } catch (Throwable awtFailure) {
                    errorKey = "msg.ccnradio.dialog_failed";
                }
            }
            Path result = selected;
            String error = errorKey;
            Minecraft.getInstance().execute(() -> {
                OPEN.set(false);
                if (error != null) {
                    onError.accept(error);
                    return;
                }
                if (result != null) {
                    File parent = result.toFile().getParentFile();
                    if (parent != null) {
                        lastDirectory = parent;
                    }
                    onSelected.accept(result);
                }
            });
        }, "CCNR-FileBrowser");
        thread.setDaemon(true);
        thread.start();
    }

    private static Path chooseWithSwing(String title, String filterLabel) throws Exception {
        Path[] selection = new Path[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Keep whatever look and feel is already installed.
            }
            JFileChooser chooser = new JFileChooser(currentDirectory());
            chooser.setDialogTitle(title);
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setAcceptAllFileFilterUsed(true);
            chooser.setFileFilter(new FileNameExtensionFilter(filterLabel, "ogg", "wav", "mp3"));

            JDialog dialog = new JDialog((Frame) null, title, true);
            final boolean[] approved = new boolean[1];
            dialog.setAlwaysOnTop(true);
            dialog.getContentPane().add(chooser);
            chooser.addActionListener(event -> {
                String command = event.getActionCommand();
                if (JFileChooser.APPROVE_SELECTION.equals(command)) {
                    approved[0] = true;
                    dialog.dispose();
                } else if (JFileChooser.CANCEL_SELECTION.equals(command)) {
                    dialog.dispose();
                }
            });
            dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            dialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent event) {
                    dialog.dispose();
                }
            });
            dialog.pack();
            dialog.setLocationRelativeTo(null);
            dialog.setVisible(true);
            File file = approved[0] ? chooser.getSelectedFile() : null;
            selection[0] = file == null ? null : file.toPath();
            dialog.dispose();
        });
        return selection[0];
    }

    private static Path chooseWithAwt(String title) throws Exception {
        Path[] selection = new Path[1];
        SwingUtilities.invokeAndWait(() -> {
            Frame owner = new Frame();
            owner.setUndecorated(true);
            owner.setSize(1, 1);
            owner.setLocationRelativeTo(null);
            owner.setAlwaysOnTop(true);
            owner.setVisible(true);
            try {
                FileDialog dialog = new FileDialog(owner, title, FileDialog.LOAD);
                dialog.setDirectory(currentDirectory().getAbsolutePath());
                dialog.setFilenameFilter((dir, name) -> {
                    String lower = name.toLowerCase(Locale.ROOT);
                    return lower.endsWith(".ogg") || lower.endsWith(".wav") || lower.endsWith(".mp3");
                });
                dialog.setAlwaysOnTop(true);
                dialog.setVisible(true);
                String file = dialog.getFile();
                String directory = dialog.getDirectory();
                if (file != null && directory != null) {
                    selection[0] = new File(directory, file).toPath();
                }
                dialog.dispose();
            } finally {
                owner.dispose();
            }
        });
        return selection[0];
    }

    private static File currentDirectory() {
        File directory = lastDirectory;
        if (directory != null && directory.isDirectory()) {
            return directory;
        }
        String home = System.getProperty("user.home");
        if (home == null || home.isEmpty()) {
            return new File(".");
        }
        File music = new File(home, "Music");
        return music.isDirectory() ? music : new File(home);
    }
}
