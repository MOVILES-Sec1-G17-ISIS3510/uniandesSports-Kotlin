# Responsive Layout Design: Adapting across Phones & Tablets

## 1. Overview
A side-by-side comparison table requires displaying multiple columns. On standard mobile screens, showing 3 columns can lead to severe text truncation. On larger screens (like tablets or landscape orientation), displaying narrow columns leaves excessive white space. 

The Coach Comparison layout adapts screen spacing, label sizes, and column sizes dynamically according to the screen width and density. This responsive design strategy is implemented **exclusively for the Coach Comparison screen**.

---

## 2. Implementation Decisions
We evaluated two layout engines:

1. **XML ConstraintLayout / Percentages**:
   * *Pros*: Well-understood.
   * *Cons*: Harder to coordinate dynamic columns inside scrollable rows in Compose.
2. **Jetpack Compose Configuration-Based Sizing (Selected)**:
   * *Pros*: Reactive, works with Compose's modifier system, and allows defining exact breakpoints.
   * *Decision*: Using the device's current width (`LocalConfiguration.current.screenWidthDp.dp`), we calculate available spaces and set responsive breakpoints (e.g., tablet vs. phone) for the table width. This responsive behavior is implemented exclusively for this table to handle up to 3 coach profiles side-by-side.

---

## 3. Code Snippets

### Sizing Breakpoint logic
In [CoachComparisonScreen.kt](file:///c:/Users/juli2/StudioProjects/uniandesSports-Kotlin/app/src/main/java/com/uniandes/sport/ui/screens/tabs/CoachComparisonScreen.kt), breakpoints are checked using screen dimensions:

```kotlin
val configuration = LocalConfiguration.current
val screenWidth = configuration.screenWidthDp.dp

// Adapt layout sizes dynamically for tablet vs. phone viewports
val isTablet = screenWidth > 600.dp
val labelColumnWidth = if (isTablet) 140.dp else 115.dp
val columnWidthMin = if (isTablet) 180.dp else 145.dp
val paddingHorizontal = 32.dp
val availableWidth = screenWidth - labelColumnWidth - paddingHorizontal

// Calculate dynamic column width: divide available space equally if they fit, else fallback to columnWidthMin
val columnWidth = remember(comparedCoaches.size, availableWidth, columnWidthMin) {
    if (comparedCoaches.isEmpty()) columnWidthMin
    else {
        val calculated = availableWidth / comparedCoaches.size
        if (calculated >= columnWidthMin) calculated else columnWidthMin
    }
}
```

### Table Structure
The layout uses a parent `Row` where:
* The left column (Headers/Labels) is static and has a fixed width.
* The right row (Coaches) is horizontally scrollable to fit any overflowing columns:

```kotlin
Row(modifier = Modifier.fillMaxWidth()) {
    // 1. Sticky Left Column (Headers)
    Column(modifier = Modifier.width(labelColumnWidth)) {
        CellHeader(height = 180.dp, label = "Coaches")
        CellLabel(height = 56.dp, label = "Sport")
        CellLabel(height = 72.dp, label = "Price / hr")
        CellLabel(height = 72.dp, label = "Rating")
        CellLabel(height = 72.dp, label = "Experience")
        CellLabel(height = 120.dp, label = "Specialty")
        ...
    }

    // 2. Horizontally Scrollable Coach Columns
    Row(
        modifier = Modifier
            .weight(1f)
            .horizontalScroll(scrollStateHorizontal)
    ) {
        comparedCoaches.forEach { coach ->
            Column(modifier = Modifier.width(columnWidth)) {
                // Column cells aligning with label heights
                ...
            }
        }
    }
}
```

---

## 4. How to Test This Feature

1. **Testing on a small phone (e.g. 5.1" screen)**:
   * Select 3 coaches.
   * Open the comparison screen.
   * **Verification**: The columns remain readable (`145.dp` wide) and overflow is scrollable horizontally.
2. **Testing on a large phone / landscape mode**:
   * Rotate the device to landscape.
   * **Verification**: The columns expand to divide the additional space evenly, filling the screen without leaving empty space.
3. **Testing on a Tablet (e.g. 10.1" Emulator)**:
   * Open the comparison screen.
   * **Verification**: The label column expands to `140.dp` for better readability, and coach columns scale up to use the tablet workspace.
