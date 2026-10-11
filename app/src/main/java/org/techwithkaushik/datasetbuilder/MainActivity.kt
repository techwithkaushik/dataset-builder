package org.techwithkaushik.datasetbuilder

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("dataset_projects", MODE_PRIVATE)
        AppLanguageState.language = preferences.getString("app_language", "English") ?: "English"
        setContent {
            val appLanguage = AppLanguageState.language
            CompositionLocalProvider(LocalAppLanguage provides appLanguage) {
            MaterialTheme {
                Surface {
                    var openedProject by remember { mutableStateOf<Triple<String, String, List<String>>?>(null) }
                    val currentProject = openedProject
                    BackHandler(enabled = currentProject != null) { openedProject = null }
                    if (currentProject == null) {
                        DatasetHomeEntry(this) { projectId, projectName, classes -> openedProject = Triple(projectId, projectName, classes) }
                    } else {
                        DatasetAnnotationScreen(
                            images = emptyList(),
                            onExit = { openedProject = null },
                            onMessage = { message ->
                                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                            },
                            projectId = currentProject.first,
                            projectName = currentProject.second,
                            classNames = currentProject.third,
                        )
                    }
                }
            }
            }
        }
    }
}
