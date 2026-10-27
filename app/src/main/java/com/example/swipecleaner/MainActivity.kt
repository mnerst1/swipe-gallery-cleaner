package com.example.swipecleaner

import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import java.util.Locale
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

private val Amber = Color(0xFFFFBD79)
private val Dark = darkColorScheme(primary = Amber, background = Color(0xFF11100F), surface = Color(0xFF1D1B19), surfaceVariant = Color(0xFF302B27), onPrimary = Color(0xFF321B08))
private val Light = lightColorScheme(primary = Color(0xFF8F4D15), background = Color(0xFFFFF8F1), surface = Color(0xFFFFF8F1), surfaceVariant = Color(0xFFF2E1D3))
class MainActivity: ComponentActivity() {
 override fun attachBaseContext(base: Context) {
  val lang = base.getSharedPreferences("gallery", 0).getString("language", "system") ?: "system"
  if (lang == "system") { super.attachBaseContext(base); return }
  val config = Configuration(base.resources.configuration); config.setLocale(Locale.forLanguageTag(lang))
  super.attachBaseContext(base.createConfigurationContext(config))
 }
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); window.statusBarColor = android.graphics.Color.TRANSPARENT; setContent { App() } }
}
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable fun App(vm: GalleryViewModel = viewModel()) {
 val s by vm.state.collectAsStateWithLifecycle(); val systemDark = isSystemInDarkTheme(); val dark = when(s.theme) { "dark" -> true; "light" -> false; else -> systemDark }
 val context = androidx.compose.ui.platform.LocalContext.current
 SideEffect { (context as? android.app.Activity)?.window?.let { window -> WindowInsetsControllerCompat(window, window.decorView).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark } } }
 val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
  if (uri != null) { try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION); vm.load(uri.toString()) } catch (_: SecurityException) { vm.navigate("error") } }
 }
  var settingsReturn by remember { mutableStateOf("welcome") }; var confirm by remember { mutableStateOf(false) }; var permissionDenied by remember { mutableStateOf(false) }; var accessForStats by rememberSaveable { mutableStateOf(false) }
 BackHandler(enabled=s.screen in listOf("settings","marked","about","filters","sessions","similar","stats")) { vm.navigate(if(s.screen=="settings") settingsReturn else if(s.screen=="about" || s.screen=="stats") "settings" else "review") }
 val mediaDelete = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { vm.completeMediaDeletion(it.resultCode == android.app.Activity.RESULT_OK) }
 val photoPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
  val full = grants[android.Manifest.permission.READ_MEDIA_IMAGES] == true || grants[android.Manifest.permission.READ_EXTERNAL_STORAGE] == true
  val partial = Build.VERSION.SDK_INT >= 34 && grants["android.permission.READ_MEDIA_VISUAL_USER_SELECTED"] == true
  if (full || partial) { permissionDenied = false; if(accessForStats) vm.refreshStats() else vm.loadAll() } else permissionDenied = true; accessForStats = false
 }
 fun selectAllPhotos(forStats:Boolean = false) {
  accessForStats = forStats
  val permission = if(Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES else android.Manifest.permission.READ_EXTERNAL_STORAGE
  if(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) { if(forStats) vm.refreshStats() else vm.loadAll(); accessForStats = false }
  else if(Build.VERSION.SDK_INT >= 34) photoPermissions.launch(arrayOf(permission, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED")) else photoPermissions.launch(arrayOf(permission))
 }
 fun deleteSelection() {
  if(s.folder == "all") {
   val uris = vm.prepareMediaBatch()
   if(uris == null) vm.navigate("error") else if(uris.isEmpty()) vm.delete() else try { mediaDelete.launch(IntentSenderRequest.Builder(MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender).build()) } catch (_: Exception) { vm.completeMediaDeletion(false) }
  } else vm.delete()
 }
 MaterialExpressiveTheme(colorScheme = if (dark) Dark else Light, motionScheme = if(s.reducedMotion) MotionScheme.standard() else MotionScheme.expressive()) {
  Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
   Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp).animateContentSize()) {
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
     if (s.screen in listOf("settings","marked","about","filters","sessions","similar","stats")) IconButton(onClick = { vm.navigate(if (s.screen == "settings") settingsReturn else if (s.screen == "about" || s.screen == "stats") "settings" else "review") }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
     else Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
     if (s.screen !in listOf("settings", "about", "stats", "working")) IconButton(onClick = { settingsReturn = s.screen; vm.navigate("settings") }) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
    }
    Crossfade(targetState=s.screen, modifier=Modifier.weight(1f), animationSpec=tween(if(s.reducedMotion) 0 else 240), label="screen transition") { screen -> Column(Modifier.fillMaxSize()) { when(screen) {
     "welcome" -> Column(Modifier.fillMaxSize().padding(bottom=24.dp), verticalArrangement = Arrangement.SpaceBetween) {
      Spacer(Modifier.height(10.dp)); Column { Text("✦", color=MaterialTheme.colorScheme.primary, fontSize=74.sp); Spacer(Modifier.height(30.dp)); Text(stringResource(R.string.welcome_tag), color=MaterialTheme.colorScheme.primary, style=MaterialTheme.typography.labelSmall, letterSpacing=2.sp); Spacer(Modifier.height(18.dp)); Text(stringResource(R.string.welcome_title), style=MaterialTheme.typography.displaySmall, fontWeight=FontWeight.SemiBold); Spacer(Modifier.height(20.dp)); Text(stringResource(R.string.welcome_body), style=MaterialTheme.typography.bodyLarge, color=MaterialTheme.colorScheme.onSurfaceVariant) }; Column { PrimaryButton(stringResource(R.string.all_photos)) { selectAllPhotos() }; Spacer(Modifier.height(10.dp)); OutlinedButton(onClick={folderPicker.launch(null)}, modifier=Modifier.fillMaxWidth().heightIn(min=56.dp), shape=RoundedCornerShape(18.dp)) { Text(stringResource(R.string.choose_folder)) }; if(permissionDenied) Text(stringResource(R.string.permission_denied), color=MaterialTheme.colorScheme.error) }
     }
     "loading", "working" -> Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) { Column(horizontalAlignment=Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(16.dp)); Text(stringResource(if(s.screen=="working") R.string.working else R.string.loading)) } }
     "empty", "error" -> Column(Modifier.fillMaxSize(), verticalArrangement=Arrangement.Center, horizontalAlignment=Alignment.CenterHorizontally) { Icon(Icons.Default.PhotoLibrary, null, Modifier.size(64.dp), tint=MaterialTheme.colorScheme.primary); Spacer(Modifier.height(24.dp)); Text(stringResource(if(s.screen=="empty") R.string.empty_title else R.string.error_title), style=MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(12.dp)); Text(stringResource(if(s.folder=="all") R.string.empty_library else if(s.screen=="empty") R.string.empty_body else R.string.error_body), textAlign=TextAlign.Center); Spacer(Modifier.height(30.dp)); PrimaryButton(stringResource(R.string.all_photos)) { selectAllPhotos() }; TextButton(onClick={folderPicker.launch(null)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(stringResource(R.string.change_folder))} }
     "review" -> if(s.photos.isEmpty()) Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center) { Text(stringResource(R.string.no_matches),style=MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(16.dp)); PrimaryButton(stringResource(R.string.filters)){vm.navigate("filters")} } else ReviewScreen(s, vm)
     "filters" -> FiltersScreen(s,vm)
     "sessions" -> SessionsScreen(s,vm)
     "similar" -> SimilarScreen(s,vm)
     "stats" -> StatisticsScreen(s,vm) { selectAllPhotos(true) }
     "marked" -> {
      val marked = s.allPhotos.filter { s.decisions[it.uri] == "delete" }; val markedBytes=marked.sumOf { it.size }; val contextForFiles=LocalContext.current; var preview by remember { mutableStateOf<Photo?>(null) }
      Text(stringResource(R.string.marked_title), style=MaterialTheme.typography.headlineMedium, fontWeight=FontWeight.SemiBold)
      Spacer(Modifier.height(8.dp)); Text(if(marked.isEmpty()) stringResource(R.string.none_marked) else stringResource(R.string.marked_body, marked.size), color=MaterialTheme.colorScheme.onSurfaceVariant)
      Text(stringResource(R.string.bytes_marked, marked.size, formatBytes(contextForFiles,markedBytes)),style=MaterialTheme.typography.titleSmall); if(s.folder == "all" && marked.isNotEmpty()) Text(stringResource(R.string.batch_next,marked.size.coerceAtMost(500),marked.size),style=MaterialTheme.typography.bodySmall)
      Spacer(Modifier.height(8.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) { TextButton(onClick={vm.markAll()}, modifier=Modifier.heightIn(min=48.dp)) { Text(stringResource(R.string.mark_filtered)) }; TextButton(onClick={vm.clearMarks()}, modifier=Modifier.heightIn(min=48.dp)) { Text(stringResource(R.string.clear_marks)) } }; if(s.folder == "all") Text(stringResource(R.string.media_batch), style=MaterialTheme.typography.bodySmall)
      LazyVerticalGrid(columns=GridCells.Adaptive(105.dp), modifier=Modifier.weight(1f), contentPadding=PaddingValues(bottom=12.dp), horizontalArrangement=Arrangement.spacedBy(10.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) { items(marked, key={it.uri}) { photo -> Column { Box { AsyncImage(model=photo.uri, contentDescription=photo.name, contentScale=androidx.compose.ui.layout.ContentScale.Crop, modifier=Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(22.dp)).clickable { preview=photo }); IconButton(onClick={ vm.removeMark(photo.uri) }, modifier=Modifier.align(Alignment.TopEnd).size(48.dp)) { Icon(Icons.Default.Cancel, stringResource(R.string.remove_mark), tint=Color.White) } }; Text(photo.name,maxLines=1,style=MaterialTheme.typography.labelMedium); Text("${formatPhotoDate(photo.date)} · ${formatBytes(contextForFiles,photo.size)}",maxLines=1,style=MaterialTheme.typography.labelSmall) } } }
      if(marked.isNotEmpty()) PrimaryButton(stringResource(R.string.delete_selected, if(s.folder == "all") marked.size.coerceAtMost(500) else marked.size)) { confirm=true } else PrimaryButton(stringResource(R.string.done)) { vm.delete() }
      TextButton(onClick={vm.undo()}, enabled=s.previous.isNotEmpty(), modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(stringResource(R.string.undo)) }; preview?.let { PhotoDialog(it) { preview=null } }
     }
     "complete" -> Column(Modifier.fillMaxSize(), verticalArrangement=Arrangement.Center, horizontalAlignment=Alignment.CenterHorizontally) { Text("✦", color=MaterialTheme.colorScheme.primary, fontSize=76.sp); Text(stringResource(R.string.complete_title), style=MaterialTheme.typography.displaySmall); Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.complete_body, s.decisions.count { it.value=="keep" }, s.deleted)); if(s.deleted>0) { Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.stats_freed,formatBytes(context,s.deletedBytes))) }; if(s.pending>0) { Spacer(Modifier.height(16.dp)); Text(stringResource(R.string.pending_count, s.pending), textAlign=TextAlign.Center); TextButton(onClick={vm.navigate("marked")}) { Text(stringResource(R.string.finish)) } }; if(s.failed>0) { Spacer(Modifier.height(16.dp)); Text(stringResource(R.string.failed_count, s.failed), textAlign=TextAlign.Center); TextButton(onClick={vm.navigate("marked")}) { Text(stringResource(R.string.finish)) } }; Spacer(Modifier.height(32.dp)); PrimaryButton(stringResource(R.string.review_again)) { vm.reset() }; TextButton(onClick={folderPicker.launch(null)}, modifier=Modifier.heightIn(min=48.dp)) { Text(stringResource(R.string.change_folder)) } }
     "settings" -> Column(Modifier.verticalScroll(rememberScrollState())) { SettingsTabs(false,vm); Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.settings), style=MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(28.dp)); Text(stringResource(R.string.theme), style=MaterialTheme.typography.titleMedium); listOf("system" to R.string.system,"dark" to R.string.dark,"light" to R.string.light).forEach { (value,label) -> Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(selected=s.theme==value, onClick={vm.setTheme(value)}, role=Role.RadioButton), verticalAlignment=Alignment.CenterVertically) { RadioButton(selected=s.theme==value,onClick=null); Spacer(Modifier.width(12.dp)); Text(stringResource(label)) } }; Spacer(Modifier.height(16.dp)); Row(Modifier.fillMaxWidth().heightIn(min=56.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween) { Text(stringResource(R.string.haptics)); Switch(checked=s.haptics,onCheckedChange=vm::setHaptics) }; HorizontalDivider(Modifier.padding(vertical=24.dp)); Text(stringResource(R.string.deletion_info), style=MaterialTheme.typography.titleMedium); Spacer(Modifier.height(10.dp)); Text(stringResource(R.string.deletion_detail), color=MaterialTheme.colorScheme.onSurfaceVariant)
      Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) { Text(stringResource(R.string.reduced_motion)); Switch(checked=s.reducedMotion,onCheckedChange=vm::setReducedMotion) }; HorizontalDivider(Modifier.padding(vertical=24.dp)); Text(stringResource(R.string.language), style=MaterialTheme.typography.titleMedium)
      val currentLang = context.getSharedPreferences("gallery", 0).getString("language", "system") ?: "system"
      listOf("system" to stringResource(R.string.system), "en" to "English", "ru" to "Русский", "kk" to "Қазақша", "es" to "Español", "fr" to "Français", "de" to "Deutsch").forEach { (code, label) -> Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(selected=currentLang==code, onClick={context.getSharedPreferences("gallery",0).edit().putString("language",code).apply(); (context as? android.app.Activity)?.recreate()}, role=Role.RadioButton), verticalAlignment=Alignment.CenterVertically) { RadioButton(selected=currentLang==code,onClick=null); Spacer(Modifier.width(12.dp)); Text(label) } }
      HorizontalDivider(Modifier.padding(vertical=20.dp)); Text(stringResource(R.string.sources), style=MaterialTheme.typography.titleMedium); TextButton(onClick={selectAllPhotos()}, modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(stringResource(R.string.all_photos)) }; TextButton(onClick={folderPicker.launch(null)}, modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(stringResource(R.string.change_folder)) }; if(permissionDenied) Text(stringResource(R.string.permission_denied), color=MaterialTheme.colorScheme.error); HorizontalDivider(Modifier.padding(vertical=20.dp)); FilledTonalButton(onClick={vm.navigate("stats")}, modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) { Icon(Icons.Default.Insights,null); Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.stats_button)) }; TextButton(onClick={vm.navigate("about")}, modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text(stringResource(R.string.about)) }
     }
     "about" -> Column(Modifier.fillMaxSize(), verticalArrangement=Arrangement.Center, horizontalAlignment=Alignment.CenterHorizontally) {
      Icon(Icons.Default.PhotoLibrary, null, Modifier.size(76.dp), tint=MaterialTheme.colorScheme.primary); Spacer(Modifier.height(20.dp)); Text(stringResource(R.string.app_name), style=MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.created_by), style=MaterialTheme.typography.bodyLarge); Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.about_description), textAlign=TextAlign.Center, color=MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(24.dp)); PrimaryButton(stringResource(R.string.github)) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mnerst1"))) }
     }
     } } }
   }
  }
  if(confirm) AlertDialog(onDismissRequest={confirm=false}, title={Text(stringResource(R.string.confirm_title))}, text={Column { Text(stringResource(if(s.folder == "all") R.string.confirm_body_media else R.string.confirm_body)); if(s.folder == "all") { Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.media_batch)) } }}, confirmButton={TextButton(onClick={confirm=false;deleteSelection()}) {Text(stringResource(R.string.confirm_delete))}}, dismissButton={TextButton(onClick={confirm=false}) {Text(stringResource(R.string.cancel))}})
 }
}
@Composable fun PrimaryButton(label: String, action: () -> Unit) { Button(onClick=action, modifier=Modifier.fillMaxWidth().heightIn(min=56.dp), shape=RoundedCornerShape(18.dp)) { Text(label, fontWeight=FontWeight.SemiBold) } }
@Composable fun ReviewScreen(s:State,vm:GalleryViewModel) {
 val photo=s.photos.getOrNull(s.index) ?: return; val haptic=LocalHapticFeedback.current; var drag by remember(photo.uri){mutableFloatStateOf(0f)}; var preview by remember {mutableStateOf(false)}
 fun decide(v:String,swipe:Boolean=false){if(s.haptics)haptic.performHapticFeedback(HapticFeedbackType.LongPress);vm.decide(v,swipe)}
 val reviewed=s.photos.count{it.uri in s.decisions}; val ctx=LocalContext.current
 BoxWithConstraints(Modifier.fillMaxSize().padding(bottom=8.dp)) {
  val wide=maxWidth>maxHeight && maxWidth>600.dp; val compact=maxHeight<600.dp || LocalDensity.current.fontScale>1.25f
  @Composable fun PhotoCard(modifier:Modifier) {
   Box(modifier.clip(RoundedCornerShape(32.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).pointerInput(photo.uri,s.reducedMotion) { detectHorizontalDragGestures(onDragEnd={if(drag>90f)decide("keep",true) else if(drag< -90f)decide("delete",true);drag=0f},onHorizontalDrag={change,amount->change.consume();drag+=amount}) },contentAlignment=Alignment.Center) {
    val rotation by animateFloatAsState(targetValue=if(s.reducedMotion)0f else (drag/45f).coerceIn(-8f,8f),label="tilt")
    Crossfade(targetState=photo.uri,animationSpec=tween(if(s.reducedMotion)0 else 220),label="photo",modifier=Modifier.fillMaxSize().graphicsLayer{rotationZ=rotation;translationX=if(s.reducedMotion)0f else drag*.25f}) { uri -> AsyncImage(model=uri,contentDescription=stringResource(R.string.photo_description,s.index+1,s.photos.size),contentScale=androidx.compose.ui.layout.ContentScale.Fit,modifier=Modifier.fillMaxSize()) }
    Surface(modifier=Modifier.align(Alignment.TopStart).padding(14.dp),shape=RoundedCornerShape(20.dp),color=Color.Black.copy(alpha=.62f)) { Text(formatPhotoDate(photo.date),modifier=Modifier.padding(horizontal=12.dp,vertical=7.dp),color=Color.White,style=MaterialTheme.typography.labelLarge) }
    IconButton(onClick={preview=true},modifier=Modifier.align(Alignment.BottomEnd).padding(8.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest,RoundedCornerShape(24.dp))) { Icon(Icons.Default.ZoomIn,stringResource(R.string.view_details)) }
    if(kotlin.math.abs(drag)>25) Surface(modifier=Modifier.align(Alignment.TopCenter).padding(16.dp),shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.primary) {Text(stringResource(if(drag>0)R.string.keep else R.string.mark),Modifier.padding(horizontal=18.dp,vertical=12.dp),color=MaterialTheme.colorScheme.onPrimary,fontWeight=FontWeight.Bold)}
   }
  }
  @Composable fun Controls(modifier:Modifier) {
   Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Text(stringResource(R.string.progress,reviewed,s.photos.size),style=MaterialTheme.typography.labelLarge); Text(stringResource(R.string.remaining,s.photos.size-reviewed),style=MaterialTheme.typography.labelMedium) }
    LinearProgressIndicator(progress={reviewed.toFloat()/s.photos.size},modifier=Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(8.dp)))
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(stringResource(R.string.kept_count,s.photos.count{s.decisions[it.uri]=="keep"}),style=MaterialTheme.typography.labelMedium); Text(stringResource(R.string.marked_count,s.photos.count{s.decisions[it.uri]=="delete"}),style=MaterialTheme.typography.labelMedium) }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { IconButton(onClick={vm.move(-1)},enabled=s.index>0){Icon(Icons.Default.ChevronLeft,stringResource(R.string.previous))}; Text("${s.index+1} / ${s.photos.size}"); IconButton(onClick={vm.move(1)},enabled=s.index<s.photos.lastIndex){Icon(Icons.Default.ChevronRight,stringResource(R.string.next))} }
    Text(photo.name,maxLines=1,style=MaterialTheme.typography.labelMedium); Text("${formatPhotoDate(photo.date)} · ${formatBytes(ctx,photo.size)}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) { FilledTonalButton(onClick={decide("delete")},modifier=Modifier.weight(1f).heightIn(min=56.dp)) {Text(stringResource(R.string.mark),maxLines=2,textAlign=TextAlign.Center)}; Button(onClick={decide("keep")},modifier=Modifier.weight(1f).heightIn(min=56.dp)){Text(stringResource(R.string.keep))} }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {TextButton(onClick={vm.undo()},enabled=s.previous.isNotEmpty()){Text(stringResource(R.string.undo))};TextButton(onClick={vm.skip()}){Text(stringResource(R.string.skip))};TextButton(onClick={vm.navigate("marked")}){Text(stringResource(R.string.finish),maxLines=1)} }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) { IconButton(onClick={vm.navigate("filters")}){Icon(Icons.Default.FilterList,stringResource(R.string.filters))}; IconButton(onClick={vm.navigate("sessions")}){Icon(Icons.Default.CollectionsBookmark,stringResource(R.string.sessions))}; IconButton(onClick={vm.scanSimilar()}){Icon(Icons.Default.Compare,stringResource(R.string.view_similar))} }
   }
  }
  if(wide) Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(18.dp)) { PhotoCard(Modifier.weight(1.15f).fillMaxHeight()); Controls(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) }
  else if(compact) Column(Modifier.fillMaxSize()) { PhotoCard(Modifier.fillMaxWidth().weight(1f)); Spacer(Modifier.height(8.dp)); Controls(Modifier.fillMaxWidth().weight(1.4f).verticalScroll(rememberScrollState())) }
  else Column(Modifier.fillMaxSize()) { PhotoCard(Modifier.fillMaxWidth().weight(1f)); Spacer(Modifier.height(8.dp)); Controls(Modifier.fillMaxWidth()) }
 }
 if(preview) PhotoDialog(photo){preview=false}
}








