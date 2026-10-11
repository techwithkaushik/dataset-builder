package org.techwithkaushik.datasetbuilder

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

val LocalAppLanguage = compositionLocalOf { "English" }

private val translations = mapOf(
    "हिन्दी" to mapOf(
        "Dataset Builder" to "डेटासेट बिल्डर", "Create, manage and annotate datasets" to "डेटासेट बनाएँ, प्रबंधित करें और एनोटेट करें",
        "Datasets" to "डेटासेट", "Stats" to "आँकड़े", "Settings" to "सेटिंग्स", "Create Dataset" to "डेटासेट बनाएँ",
        "Dataset overview" to "डेटासेट विवरण", "Projects:" to "प्रोजेक्ट:", "Annotation classes:" to "एनोटेशन क्लास:",
        "Search datasets…" to "डेटासेट खोजें…", "All" to "सभी", "No datasets found" to "कोई डेटासेट नहीं मिला",
        "Create New Dataset" to "नया डेटासेट बनाएँ", "Edit Project" to "प्रोजेक्ट संपादित करें", "Duplicate Dataset" to "डेटासेट की प्रतिलिपि बनाएँ",
        "Delete Dataset" to "डेटासेट हटाएँ", "Delete dataset?" to "डेटासेट हटाएँ?", "Delete" to "हटाएँ", "Cancel" to "रद्द करें",
        "Project name" to "प्रोजेक्ट का नाम", "Description (optional)" to "विवरण (वैकल्पिक)", "Annotation classes" to "एनोटेशन क्लास",
        "Class names" to "क्लास के नाम", "Save Changes" to "बदलाव सहेजें", "Create Dataset" to "डेटासेट बनाएँ",
        "Language" to "भाषा", "App language" to "ऐप की भाषा", "Choose the language used by the app" to "ऐप के लिए भाषा चुनें",
        "Appearance" to "दिखावट", "Theme" to "थीम", "System default" to "सिस्टम डिफ़ॉल्ट", "Light" to "लाइट", "Dark" to "डार्क",
        "Dynamic color" to "डायनेमिक रंग", "Data & Storage" to "डेटा और स्टोरेज", "Dataset storage" to "डेटासेट स्टोरेज",
        "App-private YOLO workspace" to "ऐप का निजी YOLO वर्कस्पेस", "Export format" to "एक्सपोर्ट फ़ॉर्मेट", "YOLO dataset ZIP" to "YOLO डेटासेट ZIP",
        "About" to "ऐप के बारे में", "Back to datasets" to "डेटासेट पर वापस जाएँ", "Add images" to "इमेज जोड़ें",
        "Export dataset ZIP" to "डेटासेट ZIP एक्सपोर्ट करें", "Finish and return" to "पूरा करें और वापस जाएँ",
        "Annotate" to "एनोटेट करें", "Images" to "इमेज", "Classes" to "क्लास", "Info" to "जानकारी",
        "Save & Next  →" to "सहेजें और अगला  →", "Save ✓" to "सहेजें ✓", "Previous" to "पिछला",
        "Delete this image?" to "यह इमेज हटाएँ?", "This image and its saved annotations will be permanently removed from the dataset. The original source file will remain untouched." to "यह इमेज और इसके सहेजे गए एनोटेशन डेटासेट से स्थायी रूप से हट जाएँगे। मूल फ़ाइल सुरक्षित रहेगी।",
        "Image deleted from dataset" to "इमेज डेटासेट से हटा दी गई", "Dataset ZIP exported successfully" to "डेटासेट ZIP सफलतापूर्वक एक्सपोर्ट हुई",
        "Pending" to "बाकी", "Saved" to "सहेजा गया", "Name" to "नाम", "Search" to "खोजें",
        "Select a class, then drag around each object to annotate it." to "क्लास चुनें और हर ऑब्जेक्ट के चारों ओर ड्रैग करके एनोटेट करें।",
        "Dataset is empty. Select Add images to get started." to "डेटासेट खाली है। शुरू करने के लिए इमेज जोड़ें।",
        "Move image" to "इमेज खिसकाएँ", "Draw boxes" to "बॉक्स बनाएँ", "Remove" to "हटाएँ"
    ),
    "Español" to mapOf(
        "Dataset Builder" to "Creador de conjuntos de datos", "Create, manage and annotate datasets" to "Crea, administra y anota conjuntos de datos",
        "Datasets" to "Conjuntos", "Stats" to "Estadísticas", "Settings" to "Ajustes", "Create Dataset" to "Crear conjunto",
        "Dataset overview" to "Resumen del conjunto", "Search datasets…" to "Buscar conjuntos…", "No datasets found" to "No se encontraron conjuntos",
        "Create New Dataset" to "Crear conjunto nuevo", "Edit Project" to "Editar proyecto", "Duplicate Dataset" to "Duplicar conjunto",
        "Delete Dataset" to "Eliminar conjunto", "Delete dataset?" to "¿Eliminar conjunto?", "Delete" to "Eliminar", "Cancel" to "Cancelar",
        "Project name" to "Nombre del proyecto", "Description (optional)" to "Descripción (opcional)", "Annotation classes" to "Clases de anotación",
        "Class names" to "Nombres de clase", "Save Changes" to "Guardar cambios", "Language" to "Idioma", "App language" to "Idioma de la aplicación",
        "Choose the language used by the app" to "Elige el idioma de la aplicación", "Appearance" to "Apariencia", "Theme" to "Tema",
        "System default" to "Predeterminado del sistema", "Light" to "Claro", "Dark" to "Oscuro", "Data & Storage" to "Datos y almacenamiento",
        "Dataset storage" to "Almacenamiento del conjunto", "Export format" to "Formato de exportación", "About" to "Acerca de",
        "Add images" to "Añadir imágenes", "Export dataset ZIP" to "Exportar ZIP", "Finish and return" to "Finalizar y volver",
        "Annotate" to "Anotar", "Images" to "Imágenes", "Classes" to "Clases", "Info" to "Información",
        "Save & Next  →" to "Guardar y siguiente  →", "Save ✓" to "Guardar ✓", "Previous" to "Anterior",
        "Delete this image?" to "¿Eliminar esta imagen?", "Cancel" to "Cancelar", "Saved" to "Guardado", "Pending" to "Pendiente",
        "Name" to "Nombre", "Move image" to "Mover imagen", "Draw boxes" to "Dibujar cuadros"
    ),
    "Français" to mapOf(
        "Dataset Builder" to "Créateur de jeux de données", "Create, manage and annotate datasets" to "Créer, gérer et annoter des jeux de données",
        "Datasets" to "Jeux de données", "Stats" to "Statistiques", "Settings" to "Paramètres", "Create Dataset" to "Créer un jeu",
        "Dataset overview" to "Aperçu du jeu", "Search datasets…" to "Rechercher des jeux…", "No datasets found" to "Aucun jeu trouvé",
        "Create New Dataset" to "Créer un jeu", "Edit Project" to "Modifier le projet", "Duplicate Dataset" to "Dupliquer le jeu",
        "Delete Dataset" to "Supprimer le jeu", "Delete dataset?" to "Supprimer ce jeu ?", "Delete" to "Supprimer", "Cancel" to "Annuler",
        "Project name" to "Nom du projet", "Description (optional)" to "Description (facultatif)", "Annotation classes" to "Classes d’annotation",
        "Class names" to "Noms des classes", "Save Changes" to "Enregistrer", "Language" to "Langue", "App language" to "Langue de l’application",
        "Choose the language used by the app" to "Choisissez la langue de l’application", "Appearance" to "Apparence", "Theme" to "Thème",
        "System default" to "Par défaut", "Light" to "Clair", "Dark" to "Sombre", "Data & Storage" to "Données et stockage",
        "Dataset storage" to "Stockage du jeu", "Export format" to "Format d’exportation", "About" to "À propos",
        "Add images" to "Ajouter des images", "Export dataset ZIP" to "Exporter le ZIP", "Finish and return" to "Terminer et revenir",
        "Annotate" to "Annoter", "Images" to "Images", "Classes" to "Classes", "Info" to "Infos",
        "Save & Next  →" to "Enregistrer et suivant  →", "Save ✓" to "Enregistrer ✓", "Previous" to "Précédent",
        "Delete this image?" to "Supprimer cette image ?", "Saved" to "Enregistré", "Pending" to "En attente",
        "Name" to "Nom", "Move image" to "Déplacer l’image", "Draw boxes" to "Dessiner des cadres"
    ),
    "العربية" to mapOf(
        "Dataset Builder" to "منشئ مجموعات البيانات", "Create, manage and annotate datasets" to "إنشاء مجموعات البيانات وإدارتها وتعليقها",
        "Datasets" to "مجموعات البيانات", "Stats" to "الإحصاءات", "Settings" to "الإعدادات", "Create Dataset" to "إنشاء مجموعة بيانات",
        "Dataset overview" to "نظرة عامة", "Search datasets…" to "البحث عن مجموعات البيانات…", "No datasets found" to "لم يتم العثور على مجموعات بيانات",
        "Create New Dataset" to "إنشاء مجموعة جديدة", "Edit Project" to "تعديل المشروع", "Duplicate Dataset" to "نسخ مجموعة البيانات",
        "Delete Dataset" to "حذف مجموعة البيانات", "Delete dataset?" to "هل تريد حذف مجموعة البيانات؟", "Delete" to "حذف", "Cancel" to "إلغاء",
        "Project name" to "اسم المشروع", "Description (optional)" to "الوصف (اختياري)", "Annotation classes" to "فئات التعليقات",
        "Class names" to "أسماء الفئات", "Save Changes" to "حفظ التغييرات", "Language" to "اللغة", "App language" to "لغة التطبيق",
        "Choose the language used by the app" to "اختر لغة التطبيق", "Appearance" to "المظهر", "Theme" to "السمة",
        "System default" to "افتراضي النظام", "Light" to "فاتح", "Dark" to "داكن", "Data & Storage" to "البيانات والتخزين",
        "Dataset storage" to "تخزين مجموعة البيانات", "Export format" to "تنسيق التصدير", "About" to "حول",
        "Add images" to "إضافة صور", "Export dataset ZIP" to "تصدير ZIP", "Finish and return" to "إنهاء والعودة",
        "Annotate" to "تعليق", "Images" to "الصور", "Classes" to "الفئات", "Info" to "معلومات",
        "Save & Next  →" to "حفظ والتالي  →", "Save ✓" to "حفظ ✓", "Previous" to "السابق",
        "Delete this image?" to "هل تريد حذف هذه الصورة؟", "Saved" to "تم الحفظ", "Pending" to "قيد الانتظار",
        "Name" to "الاسم", "Move image" to "تحريك الصورة", "Draw boxes" to "رسم المربعات"
    )
)

fun localizedText(value: String, language: String): String {
    val dictionary = translations[language] ?: return value
    return dictionary[value] ?: value
}

@Composable
fun LText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    val language = LocalAppLanguage.current
    androidx.compose.material3.Text(
        text = localizedText(text, language), modifier = modifier, color = color, fontSize = fontSize,
        fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing,
        textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight, overflow = overflow,
        softWrap = softWrap, maxLines = maxLines, minLines = minLines, onTextLayout = onTextLayout, style = style
    )
}
