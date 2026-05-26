# 📊 MyStats Debug Logs Guide

He agregado logs detallados y fáciles de buscar para debugging. Todos usan el prefijo **`📊 MYSTATS:`** para identificarlos rápidamente.

## 🔍 Cómo buscar los logs

En **Android Studio → Logcat** (o en terminal):

```bash
adb logcat | grep "📊 MYSTATS:"
```

O en Android Studio:
1. Abre **Logcat** (View → Tool Windows → Logcat o atajo Alt+6)
2. En el campo de búsqueda, escribe: `📊 MYSTATS:`
3. Filtra por nivel **Debug** (verde)

## 📋 Flujo de logs esperado

Cuando abras la pantalla de estadísticas, deberías ver estos logs **en orden**:

### 1️⃣ **ViewModel Inicialización**
```
🎯 MyStatsViewModel INIT for userId: [YOUR_UID_HERE]
```
Si **NO ves esto**: El ViewModel no se está creando. Revisa la navigación.

---

### 2️⃣ **Repository empieza a obtener datos**
```
🚀 START getStats() for userId: [YOUR_UID_HERE] | forceRefresh: false
```
Si **NO ves esto**: El repository no se está llamando.

---

### 3️⃣ **Búsqueda desde Room Cache (debería ser rápido)**
```
💾 From Room Cache: events=0 posts=0 km=0.0 hasRealData=false
```
- `hasRealData=false` → Es la primera vez
- `events=X` → Ya hay datos en cache local

---

### 4️⃣ **Sincronización desde Firestore**
```
🔄 SYNCING from Firestore...
📍 calculateRealStats() START for userId: [YOUR_UID_HERE]
```

#### 🎯 Búsqueda de EVENTOS:
```
📋 Checking [N] total events for membership of [YOUR_UID_HERE]
✓ Event found: [EVENT_ID] (si participas)
🏌️ EVENTS TOTAL: [X] events where user is member
```
- Si ves `EVENTS TOTAL: 0` pero participaste en open matches → **PROBLEMA EN LA BÚSQUEDA DE EVENTOS**

#### 📝 Búsqueda de POSTS:
```
📋 Searching posts in communities...
📋 Found [N] communities
✓ Found [X] posts in community: [COMMUNITY_ID]
🏌️ POSTS TOTAL: [X] posts for user: [YOUR_UID_HERE]
```
- Si ves `POSTS TOTAL: 0` pero escribiste en comunidades → **PROBLEMA EN LA BÚSQUEDA DE POSTS**

#### 🏃 Búsqueda de RUNS (CORRIDAS):
```
📋 Searching runs at users/[UID]/runs...
📋 Found [N] runs
✓ Run [RUN_ID]: [X.XX]km
🏌️ RUNS TOTAL: [X.XX] km for user: [YOUR_UID_HERE]
```
- Si ves `RUNS TOTAL: 0` pero corriste → **PROBLEMA EN LA BÚSQUEDA DE RUNS**

---

### 5️⃣ **Resultado Final**
```
✅ RESULT: level=2 points=115 streak=5 (events=2, posts=10, km=15.50)
✅ From Firestore Sync: events=2 posts=10 km=15.50 hasRealData=true
```

---

### 6️⃣ **ViewModel recibe datos**
```
📥 ViewModel received stats: events=2 posts=10 km=15.50 level=2
🔄 ViewModel sync status: IDLE
```

---

### 7️⃣ **UI se renderiza**
```
🖼️ MyStatsScreen RENDER: stats=2/10/15.5 | syncStatus=IDLE | isLoading=false
```

---

## ⚠️ Problemas comunes y qué logs buscar

### ❌ Ves `EVENTS TOTAL: 0` pero tienes open matches:
- Problema: La ruta `db.collection("events")` podría tener eventos pero el userId no está en la subcollection `members`
- **Próximo paso**: Mostra logs de `✓ Event found:` - si no ves ninguno, el userId no es miembro

### ❌ Ves `POSTS TOTAL: 0` pero escribiste en comunidades:
- Problema: La ruta `db.collection("communities").{id}.collection("posts")` está mal o el field `author` es diferente
- **Próximo paso**: Ver logs de `✓ Found [X] posts in community` - si ves 0 posts en TODAS las comunidades, hay problema

### ❌ Ves `RUNS TOTAL: 0` pero corriste:
- Problema: La ruta `users/{userId}/runs` podría estar mal o no existen datos
- **Próximo paso**: Ver si ves log `📋 Found [N] runs` - si ves 0, la subcollection está vacía

### ❌ No ves NINGÚN log:
- Problema: La pantalla no se está renderizando o userId es vacío
- **Próximo paso**: Verifica que estés logueado: revisa `🎯 MyStatsViewModel INIT` - si no está, el usuario NO está logueado

---

## 📤 Cómo compartir logs conmigo

1. Abre **Logcat**
2. Filtra por: `📊 MYSTATS:`
3. Ejecuta estas acciones en orden:
   - Logout y login
   - Navega a la pantalla de estadísticas
   - Copia TODOS los logs que ves
   - Comparte conmigo el log completo

**Comando para exportar logs:**
```bash
adb logcat | grep "📊 MYSTATS:" > mystats_logs.txt
cat mystats_logs.txt
```

---

## 🔧 Cómo I desactivar logs (cuando funcione todo)

En `MyStatsRepository.kt` y `MyStatsViewModel.kt`, cambia:
```kotlin
Log.d("📊 MYSTATS:", "...")  // Debug (verbose)
```
A:
```kotlin
Log.v("📊 MYSTATS:", "...")  // Verbose (menos visibles)
```

O simplemente saca los logs cuando confirmes que funciona.
