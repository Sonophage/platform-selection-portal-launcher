package com.psplauncher.studio.io

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

object FileDialogs {
    fun openFile(parent: Frame?, title: String, extensions: Set<String>): File? {
        val dialog = FileDialog(parent, title, FileDialog.LOAD)
        dialog.setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in extensions }
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val name = dialog.file ?: return null
        return File(dir, name)
    }

    fun saveFile(parent: Frame?, title: String, suggestedName: String, extension: String): File? {
        val dialog = FileDialog(parent, title, FileDialog.SAVE)
        dialog.file = suggestedName
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val name = dialog.file ?: return null
        val fixed = if (name.lowercase().endsWith(".$extension")) name else "$name.$extension"
        return File(dir, fixed)
    }

    fun pickDirectory(title: String): File? {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isMultiSelectionEnabled = false
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }
}
