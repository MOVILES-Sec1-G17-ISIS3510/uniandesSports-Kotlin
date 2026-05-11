# 📚 Multithreading & Async - Complete Knowledge Base

## Overview

Documentos para defender implementación de **Multithreading & Asincronismo en Kotlin** en Viva Voce.

| Documento | Propósito | Duración |
|-----------|-----------|----------|
| **multithreading-async-viva-voce.md** | Defensa oral detallada | 20-30 min lectura |
| **async-quick-reference.md** | Cheat sheet compacto | 5-10 min lectura |
| **rubrica-kotlin-score-maximizer.md** | Maximizar puntaje | 10-15 min lectura |
| **async-visual-guide.md** | Diagramas y flujos | 10 min lectura |
| **EXAM-CHEAT-SHEET.md** | Exam day compact | 1-2 min skim |
| **SUMMARY.md** | Executive overview | 5 min lectura |

---

## 1. Viva Voce Completo

**multithreading-async-viva-voce.md**

- ✅ Qué evalúa profesor
- ✅ Qué implementamos (arquitectura)
- ✅ Archivos importantes con líneas
- ✅ Walkthrough línea por línea de 6 patrones
- ✅ Por qué decisiones arquitectónicas
- ✅ Riesgos y limitaciones
- ✅ Vocabulario técnico
- ✅ 8 preguntas evaluador + respuestas
- ✅ Cómo defender oralmente

**Usa cuando:** Necesitas respuesta completa (3-5 min)

---

## 2. Quick Reference

**async-quick-reference.md**

- ✅ Tabla rápida dispatchers
- ✅ 5 patrones copy-paste
- ✅ Anti-patrones con soluciones
- ✅ IO vs Main decision tree
- ✅ Frases técnicas
- ✅ Score por tema

**Usa cuando:** Necesitas 30-60 segundos

---

## 3. Rúbrica & Score

**rubrica-kotlin-score-maximizer.md**

- ✅ Rúbrica por criterio
- ✅ Score actual (9.2/10 = 92%)
- ✅ Top 3 puntos destacar
- ✅ Top 3 errores evitar
- ✅ GlobalScope explicación
- ✅ Pre-exam checklist

**Usa cuando:** Necesitas estrategia

---

## 4. Visual Guide

**async-visual-guide.md**

- ✅ Diagramas arquitectura
- ✅ Timeline parallelismo
- ✅ Dispatcher chain visual
- ✅ Thread pool visualization
- ✅ Prevention checklist

**Usa cuando:** Necesitas entender visualmente

---

## 5. Exam Cheat Sheet

**EXAM-CHEAT-SHEET.md**

- ✅ 3 primeras cosas
- ✅ 10 términos técnicos
- ✅ 4 archivos críticos
- ✅ Respuestas memorizadas
- ✅ Red flags

**Usa cuando:** Exam day (1 min skim)

---

## 6. Executive Summary

**SUMMARY.md**

- ✅ One-page snapshot
- ✅ Estadísticas proyecto
- ✅ Score breakdown
- ✅ Risk assessment

**Usa cuando:** Overview rápido

---

## 📊 Estadísticas Proyecto

- **80+** corrutinas en ViewModels/Screens
- **90+** StateFlow declarations
- **40+** Dispatchers.IO operations
- **20+** Dispatchers.Main operations
- **50+** LaunchedEffect uses
- **9.2/10** Expected score (92%)
- **1** Anti-pattern (GlobalScope)

---

## 🎯 Quick Navigation

### "¿Diferencia launch vs async?"
- Quick: [async-quick-reference.md](async-quick-reference.md)
- Full: [multithreading-async-viva-voce.md](multithreading-async-viva-voce.md#3-patrón-coroutinescope--async---paralelismo)
- Visual: [async-visual-guide.md](async-visual-guide.md)

### "¿Por qué IO dispatcher?"
- Quick: [async-quick-reference.md](async-quick-reference.md#io-vs-main-dispatcher-quick-decision-tree)
- Full: [multithreading-async-viva-voce.md](multithreading-async-viva-voce.md#1-evitar-bloqueos-del-main-thread)
- Score: [rubrica-kotlin-score-maximizer.md](rubrica-kotlin-score-maximizer.md#criterio-2-conocimiento-de-dispatchers-peso-20)

### "¿Cache-first?"
- Quick: [async-quick-reference.md](async-quick-reference.md#cosas-que-gritan-no-entiendo)
- Full: [multithreading-async-viva-voce.md](multithreading-async-viva-voce.md#2-cache-first-strategy-para-latencia-percibida)
- Visual: [async-visual-guide.md](async-visual-guide.md#-performance-cachefirst)

### "¿Parallelismo?"
- Quick: [async-quick-reference.md](async-quick-reference.md#pattern-2-coroutinescope--async---parallelismo)
- Full: [multithreading-async-viva-voce.md](multithreading-async-viva-voce.md#3-patrón-coroutinescope--async---paralelismo)
- Visual: [async-visual-guide.md](async-visual-guide.md#-timeline-parallel-vs-sequential)

### "¿GlobalScope error?"
- Quick: [async-quick-reference.md](async-quick-reference.md#️-anti-patrones-identificados)
- Full: [multithreading-async-viva-voce.md](multithreading-async-viva-voce.md#️-anti-patrón-globalscope-memory-leak)
- Score: [rubrica-kotlin-score-maximizer.md](rubrica-kotlin-score-maximizer.md#globalscopecómo-explicarlo)

---

## Estudio Recomendado

### Día 1 (2h)
1. Leer [multithreading-async-viva-voce.md](multithreading-async-viva-voce.md) (1.5h)
2. Notas en 6 patrones (0.5h)

### Día 2 (1h)
1. Memorizar frases [async-quick-reference.md](async-quick-reference.md) (0.5h)
2. Revisar checklist (0.5h)

### Día 3 (1h)
1. Rúbrica puntuación [rubrica-kotlin-score-maximizer.md](rubrica-kotlin-score-maximizer.md) (0.5h)
2. Ensayar respuestas 30-60 seg (0.5h)

### Día 4 (30 min - exam day)
1. Skim [async-visual-guide.md](async-visual-guide.md) (10 min)
2. Mental checklist (10 min)
3. Respirar hondo (10 min)

---

## Defensa Ejemplo (5 min)

"Coroutines + MVVM + Compose. 80+ corrutinas, todas en viewModelScope para lifecycle safety. IO para BD/red, Main para UI—evita lag. Parallelismo con async: 3 queries en 300ms vs 900ms. Cache-first: 10ms local, 500ms remoto. StateFlow 90+ decls, debounce búsquedas. onCleared() cancela jobs + removemos listeners. GlobalScope línea 106 es error conocido. Score esperado 9.2/10."

---

## Citas Importantes

- "Structured concurrency—si uno falla, todos se cancelan"
- "ViewModelScope auto-cancels en onCleared()"
- "Cache-first optimiza latencia percibida"
- "Debounce reduce queries 1000→1 por segundo"
- "GlobalScope es memory leak, usa viewModelScope"

---

**Status: ✅ Listo para Viva Voce (92% expected)**
