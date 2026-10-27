package com.example.swipecleaner

import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

@Composable fun FeatureCard(modifier:Modifier=Modifier, content:@Composable ColumnScope.()->Unit) {
 Surface(modifier=modifier, shape=RoundedCornerShape(28.dp), color=MaterialTheme.colorScheme.surfaceContainerHigh) { Column(Modifier.padding(20.dp), content=content) }
}
@Composable fun FiltersScreen(s:State, vm:GalleryViewModel) {
 var album by remember(s.album){mutableStateOf(s.album)}; var sort by remember(s.sort){mutableStateOf(s.sort)}; var from by remember(s.from){mutableStateOf(s.from)}; var to by remember(s.to){mutableStateOf(s.to)}
 val dateValid=listOf(from,to).all { it.isBlank() || runCatching{java.time.LocalDate.parse(it)}.isSuccess } && (from.isBlank() || to.isBlank() || from<=to)
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=24.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
  Text(stringResource(R.string.filters),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
  FeatureCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.album),style=MaterialTheme.typography.titleMedium); LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { item { FilterChip(selected=album.isEmpty(),onClick={album=""},label={Text(stringResource(R.string.all_albums))}) }; items(s.allPhotos.map{it.album}.filter{it.isNotBlank()}.distinct().sorted()) { a -> FilterChip(selected=album==a,onClick={album=a},label={Text(a)}) } } }
  FeatureCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.sort_by),style=MaterialTheme.typography.titleMedium); Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("newest" to R.string.newest,"oldest" to R.string.oldest,"largest" to R.string.largest).forEach {(key,label)-> FilterChip(selected=sort==key,onClick={sort=key},label={Text(stringResource(label),maxLines=1)}) } } }
  FeatureCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.date_range),style=MaterialTheme.typography.titleMedium); Text(stringResource(R.string.date_hint),style=MaterialTheme.typography.bodySmall); Spacer(Modifier.height(8.dp)); OutlinedTextField(from,{from=it},label={Text(stringResource(R.string.from_date))},singleLine=true,isError=!dateValid,modifier=Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp)); OutlinedTextField(to,{to=it},label={Text(stringResource(R.string.to_date))},singleLine=true,isError=!dateValid,modifier=Modifier.fillMaxWidth()); if(!dateValid) Text(stringResource(R.string.invalid_date),color=MaterialTheme.colorScheme.error) }
  Button(onClick={vm.filters(album,sort,from,to);vm.navigate("review")},enabled=dateValid,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {Text(stringResource(R.string.apply_filters))}
  TextButton(onClick={vm.filters("","newest","","");vm.navigate("review")},modifier=Modifier.fillMaxWidth()) {Text(stringResource(R.string.clear_filters))}
 }
}
@Composable fun SessionsScreen(s:State,vm:GalleryViewModel) {
 var name by remember { mutableStateOf("") }
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(14.dp)) {
  Text(stringResource(R.string.sessions),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
  Text(stringResource(R.string.sessions_info),style=MaterialTheme.typography.bodyLarge)
  s.sessions.forEach { entry -> Surface(onClick={vm.selectSession(entry);vm.navigate("review")}, shape=RoundedCornerShape(24.dp), color=if(entry==s.session) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, modifier=Modifier.fillMaxWidth()) { Row(Modifier.padding(20.dp),horizontalArrangement=Arrangement.SpaceBetween) { Text(if(entry=="Default") stringResource(R.string.default_session) else entry, style=MaterialTheme.typography.titleMedium); if(entry==s.session) Icon(Icons.Default.Check,null) } } }
  OutlinedTextField(name,{name=it.take(60)},label={Text(stringResource(R.string.session_name))},singleLine=true,modifier=Modifier.fillMaxWidth())
  Button(onClick={vm.createSession(name);vm.navigate("review")},enabled=name.isNotBlank() && name.trim() !in s.sessions,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text(stringResource(R.string.new_session)) }
 }
}
@Composable fun SettingsTabs(stats:Boolean, vm:GalleryViewModel) {
 TabRow(selectedTabIndex=if(stats)1 else 0) { Tab(selected=!stats,onClick={vm.navigate("settings")},text={Text(stringResource(R.string.settings))}); Tab(selected=stats,onClick={vm.navigate("stats")},text={Text(stringResource(R.string.stats))}) }
}
@Composable fun StatisticsScreen(s:State,vm:GalleryViewModel, requestAccess:()->Unit) {
 val ctx=LocalContext.current; val kept=s.stats.sumOf{it.kept}; val swipe=s.stats.sumOf{it.swipeKept}; val deleted=s.stats.sumOf{it.deleted}; val bytes=s.stats.sumOf{it.bytes}
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  item { SettingsTabs(true,vm); Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.stats),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold); Text(stringResource(R.string.stats_since),style=MaterialTheme.typography.bodyMedium) }
  item { FeatureCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.photos_on_device),style=MaterialTheme.typography.titleMedium); Text(s.accessibleCount?.toString() ?: "—",style=MaterialTheme.typography.displaySmall,color=MaterialTheme.colorScheme.primary); Text(stringResource(if(s.partialAccess) R.string.partial_count else if(s.accessibleCount==null) R.string.count_permission else R.string.accessible_count),style=MaterialTheme.typography.bodySmall); if(s.accessibleCount==null || s.partialAccess) TextButton(onClick=requestAccess) {Text(stringResource(R.string.all_photos))} } }
  item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) { FeatureCard(Modifier.weight(1f)) {Text(stringResource(R.string.stats_deleted)); Text(deleted.toString(),style=MaterialTheme.typography.headlineLarge)}; FeatureCard(Modifier.weight(1f)) {Text(stringResource(R.string.stats_swipe)); Text(swipe.toString(),style=MaterialTheme.typography.headlineLarge)} } }
  item { FeatureCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.stats_kept, kept)); Spacer(Modifier.height(4.dp)); Text(stringResource(R.string.stats_freed,formatBytes(ctx,bytes)),style=MaterialTheme.typography.titleMedium) } }
  item { Text(stringResource(R.string.activity_history),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold) }
  if(s.stats.isEmpty()) item {Text(stringResource(R.string.no_stats))}
  items(s.stats) { d -> FeatureCard(Modifier.fillMaxWidth()) { Text(d.day,style=MaterialTheme.typography.titleMedium); Text(stringResource(R.string.day_summary,d.kept,d.swipeKept,d.deleted,formatBytes(ctx,d.bytes)),style=MaterialTheme.typography.bodyMedium); d.photos?.let { count -> Text(stringResource(if(d.partial) R.string.day_photos_partial else R.string.day_photos,count),style=MaterialTheme.typography.bodySmall) } } }
  item { OutlinedButton(onClick={vm.refreshStats()},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(stringResource(R.string.refresh))} }
 }
}
@Composable fun SimilarScreen(s:State,vm:GalleryViewModel) {
 var selected by remember { mutableStateOf<Photo?>(null) }
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
  item { Text(stringResource(R.string.similar),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold); Text(stringResource(R.string.similar_explain),style=MaterialTheme.typography.bodyMedium) }
  if(s.scanProgress<s.scanTotal) item { LinearProgressIndicator(progress={s.scanProgress.toFloat()/s.scanTotal.coerceAtLeast(1)},modifier=Modifier.fillMaxWidth()); Text(stringResource(R.string.scan_progress,s.scanProgress,s.scanTotal)) }
  else if(s.similar.isEmpty()) item { Text(stringResource(R.string.no_similar)) }
  items(s.similar.size) { index -> val group=s.similar[index]; FeatureCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.similar_group,index+1),style=MaterialTheme.typography.titleMedium); LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) { items(group.size) { i -> val p=group[i]; Column(Modifier.width(150.dp)) { AsyncImage(model=p.uri,contentDescription=p.name,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(18.dp)).clickable{selected=p}); Text(p.name,maxLines=1,overflow=TextOverflow.Ellipsis); FilterChip(selected=s.decisions[p.uri]=="delete",onClick={vm.toggleMark(p.uri)},label={Text(stringResource(if(s.decisions[p.uri]=="delete") R.string.remove_mark else R.string.mark))}) } } } } }
 }
 selected?.let{PhotoDialog(it){selected=null}}
}
@Composable fun PhotoDialog(photo:Photo, dismiss:()->Unit) {
 val context=LocalContext.current; var scale by remember(photo.uri){mutableFloatStateOf(1f)}; var x by remember(photo.uri){mutableFloatStateOf(0f)}; var y by remember(photo.uri){mutableFloatStateOf(0f)}
 Dialog(onDismissRequest=dismiss) { Surface(shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().fillMaxHeight(.85f)) { Column(Modifier.padding(16.dp)) {
  Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).pointerInput(photo.uri) { detectTransformGestures { _,pan,zoom,_ -> scale=(scale*zoom).coerceIn(1f,5f); if(scale>1f){x+=pan.x;y+=pan.y}else{x=0f;y=0f} } }.pointerInput(photo.uri){ detectTapGestures(onDoubleTap={if(scale>1f){scale=1f;x=0f;y=0f}else{scale=2f}}) }, contentAlignment=Alignment.Center) { AsyncImage(model=Uri.parse(photo.uri),contentDescription=photo.name,contentScale=ContentScale.Fit,modifier=Modifier.fillMaxSize().graphicsLayer{scaleX=scale;scaleY=scale;translationX=x;translationY=y}) }
  Spacer(Modifier.height(12.dp)); Text(photo.name,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis); Text("${formatPhotoDate(photo.date)} · ${formatBytes(context,photo.size)}",style=MaterialTheme.typography.bodyMedium); Text(stringResource(R.string.zoom_hint),style=MaterialTheme.typography.bodySmall); TextButton(onClick=dismiss,modifier=Modifier.align(Alignment.End)){Text(stringResource(R.string.done))}
 } } }
}


