package com.arturpelicho.calcexpress.core

/** Text in the app's languages: en (default), pt, fr, de, it, es. */
data class Tr(val en: String, val pt: String, val fr: String, val de: String, val it: String, val es: String) {
    fun get(lang: String) = when (lang) {
        "pt" -> pt; "fr" -> fr; "de" -> de; "it" -> it; "es" -> es; else -> en
    }
}

/** The two big groups of the formula list. */
enum class FormulaArea(val title: Tr) {
    MATHS(Tr("Mathematics", "Matemática", "Mathématiques", "Mathematik", "Matematica", "Matemáticas")),
    ENGINEERING(Tr("Engineering", "Engenharia", "Ingénierie", "Ingenieurwesen", "Ingegneria", "Ingeniería")),
}

/** Formula sections, in the order they are listed. */
enum class FormulaCategory(val area: FormulaArea, val title: Tr) {
    GENERAL(FormulaArea.MATHS, Tr("General", "Geral", "Général", "Allgemein", "Generale", "General")),
    CALCULUS(FormulaArea.MATHS, Tr("Calculus & series", "Cálculo e séries", "Analyse et séries", "Analysis & Reihen", "Analisi e serie", "Cálculo y series")),
    MATRICES(FormulaArea.MATHS, Tr("Matrices", "Matrizes", "Matrices", "Matrizen", "Matrici", "Matrices")),
    MECHANICAL(FormulaArea.ENGINEERING, Tr("Mechanics", "Mecânica", "Mécanique", "Mechanik", "Meccanica", "Mecánica")),
    ELASTICITY(FormulaArea.ENGINEERING, Tr("Stress & strain", "Tensões e deformações", "Contraintes et déformations", "Spannungen & Dehnungen", "Tensioni e deformazioni", "Tensiones y deformaciones")),
    BEAMS(FormulaArea.ENGINEERING, Tr("Beams, torsion & buckling", "Vigas, torção e encurvadura", "Poutres, torsion et flambement", "Biegung, Torsion & Knickung", "Travi, torsione e instabilità", "Vigas, torsión y pandeo")),
    STRESS_STATE(FormulaArea.ENGINEERING, Tr("Stress states & failure criteria", "Estados de tensão e critérios de cedência", "États de contrainte et critères de rupture", "Spannungszustände & Festigkeitshypothesen", "Stati tensionali e criteri di resistenza", "Estados de tensión y criterios de fallo")),
    FLUIDS(FormulaArea.ENGINEERING, Tr("Fluid mechanics", "Mecânica dos fluidos", "Mécanique des fluides", "Strömungsmechanik", "Meccanica dei fluidi", "Mecánica de fluidos")),
    ELECTRO(FormulaArea.ENGINEERING, Tr("Electrotechnics", "Eletrotecnia", "Électrotechnique", "Elektrotechnik", "Elettrotecnica", "Electrotecnia")),
    COMPUTER(FormulaArea.ENGINEERING, Tr("Computing", "Informática", "Informatique", "Informatik", "Informatica", "Informática")),
}

/** A variable of a formula with its unit in each unit system. */
data class FormulaVar(val label: String, val metric: String, val imperial: String = metric)

class Formula(
    val id: String,
    val category: FormulaCategory,
    val name: Tr,
    /** Preview text shown in the list. */
    val preview: String,
    val vars: List<FormulaVar> = emptyList(),
    val build: (UnitSystem) -> List<MathRow>,
) {
    /** The formula's rows with every box tagged with its unit, so the unit bar can offer alternatives. */
    fun rows(sys: UnitSystem): List<MathRow> = build(sys).also { lines ->
        for (line in lines) for (r in line.allRows()) for (n in r.items) {
            if (n is Slot && n.label != null) n.baseUnit = unitFor(n.label, sys)
        }
    }

    /** Unit of the variable [label]: exact match first, then its base symbol (σ_x → σ, v₁ → v). */
    fun unitFor(label: String, sys: UnitSystem): String? {
        val byName = vars.flatMap { v -> v.label.split(",").map { it.trim() to v } }
        val base = label.substringBefore('_').trimEnd { it in "₀₁₂₃₄₅₆₇₈₉" }
        val v = byName.firstOrNull { it.first == label }?.second ?: byName.firstOrNull { it.first == base }?.second
        return v?.let { if (sys == UnitSystem.METRIC) it.metric else it.imperial }
    }
}

/** Small DSL that builds editor rows. */
class RowBuilder {
    val row = MathRow()
    fun slot(label: String) { row.items += Slot(label) }
    fun case() { row.items += Slot() }
    fun v(name: String) { row.items += Sym.variable(name) }
    fun num(text: String) {
        for (c in text) row.items += if (c == '.') Sym.point() else Sym.digit(c)
    }
    fun plus() { row.items += Sym.op(Sym.PLUS) }
    fun minus() { row.items += Sym.op(Sym.MINUS) }
    fun times() { row.items += Sym.op(Sym.DOT) }
    fun eq() { row.items += Sym.equals() }
    fun pi() { row.items += Sym.constant(Sym.PI) }
    fun e() { row.items += Sym.constant(Sym.E) }
    fun pow(block: RowBuilder.() -> Unit) { row.items += Power(build(block)) }
    fun sq() = pow { num("2") }
    fun group(block: RowBuilder.() -> Unit) { row.items += Group(build(block)) }
    fun frac(num: RowBuilder.() -> Unit, den: RowBuilder.() -> Unit) { row.items += Fraction(build(num), build(den)) }
    fun sqrt(block: RowBuilder.() -> Unit) { row.items += Root(build(block)) }
    fun fn(f: Fn, block: RowBuilder.() -> Unit) { row.items += FuncCall(f, build(block)) }
    fun integral(lo: RowBuilder.() -> Unit, hi: RowBuilder.() -> Unit, variable: String, body: RowBuilder.() -> Unit) {
        row.items += Integral(build(lo), build(hi), build(body), build { v(variable) })
    }
    fun matrix(rows: Int, cols: Int, cells: List<RowBuilder.() -> Unit> = emptyList()) {
        row.items += MatrixNode(rows, cols, MutableList(rows * cols) { i -> cells.getOrNull(i)?.let { build(it) } ?: MathRow() })
    }
    fun derivative(variable: String, body: RowBuilder.() -> Unit) { row.items += Derivative(build { v(variable) }, build(body)) }

    companion object {
        fun build(block: RowBuilder.() -> Unit): MathRow = RowBuilder().apply(block).row
    }
}

private fun line(block: RowBuilder.() -> Unit) = listOf(RowBuilder.build(block))

object Formulas {
    private const val G_METRIC = "9.80665"
    private const val G_IMPERIAL = "32.174"
    private fun g(sys: UnitSystem) = if (sys == UnitSystem.METRIC) G_METRIC else G_IMPERIAL

    val all: List<Formula> = listOf(
        // ---------------------------------------------------------------- general
        Formula(
            "quadratic", FormulaCategory.GENERAL,
            Tr("Quadratic equation", "Equação do 2.º grau", "Équation du second degré", "Quadratische Gleichung", "Equazione di secondo grado", "Ecuación de segundo grado"),
            "a·x² + b·x + c = 0",
        ) { line { slot("a"); v("x"); sq(); plus(); slot("b"); v("x"); plus(); slot("c"); eq(); num("0") } },
        Formula(
            "pythagoras", FormulaCategory.GENERAL,
            Tr("Pythagorean theorem", "Teorema de Pitágoras", "Théorème de Pythagore", "Satz des Pythagoras", "Teorema di Pitagora", "Teorema de Pitágoras"),
            "c² = a² + b²",
        ) { line { slot("c"); sq(); eq(); slot("a"); sq(); plus(); slot("b"); sq() } },
        Formula(
            "circle_area", FormulaCategory.GENERAL,
            Tr("Area of a circle", "Área do círculo", "Aire du disque", "Kreisfläche", "Area del cerchio", "Área del círculo"),
            "A = π·r²",
        ) { line { slot("A"); eq(); pi(); slot("r"); sq() } },
        Formula(
            "sphere_volume", FormulaCategory.GENERAL,
            Tr("Volume of a sphere", "Volume da esfera", "Volume de la sphère", "Kugelvolumen", "Volume della sfera", "Volumen de la esfera"),
            "V = 4/3·π·r³",
        ) { line { slot("V"); eq(); frac({ num("4") }, { num("3") }); pi(); slot("r"); pow { num("3") } } },
        Formula(
            "cylinder_volume", FormulaCategory.GENERAL,
            Tr("Volume of a cylinder", "Volume do cilindro", "Volume du cylindre", "Zylindervolumen", "Volume del cilindro", "Volumen del cilindro"),
            "V = π·r²·h",
        ) { line { slot("V"); eq(); pi(); slot("r"); sq(); times(); slot("h") } },
        Formula(
            "distance", FormulaCategory.GENERAL,
            Tr("Distance between two points", "Distância entre dois pontos", "Distance entre deux points", "Abstand zweier Punkte", "Distanza tra due punti", "Distancia entre dos puntos"),
            "d = √((x₂−x₁)² + (y₂−y₁)²)",
        ) {
            line {
                slot("d"); eq()
                sqrt { group { slot("x₂"); minus(); slot("x₁") }; sq(); plus(); group { slot("y₂"); minus(); slot("y₁") }; sq() }
            }
        },
        Formula(
            "percent_change", FormulaCategory.GENERAL,
            Tr("Percentage change", "Variação percentual", "Variation en pourcentage", "Prozentuale Änderung", "Variazione percentuale", "Variación porcentual"),
            "p = (v₂ − v₁)/v₁ · 100",
        ) { line { slot("p"); eq(); frac({ slot("v₂"); minus(); slot("v₁") }, { slot("v₁") }); times(); num("100") } },
        Formula(
            "compound_interest", FormulaCategory.GENERAL,
            Tr("Compound interest", "Juro composto", "Intérêts composés", "Zinseszins", "Interesse composto", "Interés compuesto"),
            "A = P·(1 + r/n)^(n·t)",
        ) {
            line {
                slot("A"); eq(); slot("P"); times()
                group { num("1"); plus(); frac({ slot("r") }, { slot("n") }) }
                pow { slot("n"); times(); slot("t") }
            }
        },
        // ---------------------------------------------------------------- calculus
        Formula(
            "integral", FormulaCategory.CALCULUS,
            Tr("Definite integral", "Integral definido", "Intégrale définie", "Bestimmtes Integral", "Integrale definito", "Integral definida"),
            "∫ₐᵇ f(x) dx",
        ) { line { integral({ case() }, { case() }, "x") { } } },
        Formula(
            "double_integral", FormulaCategory.CALCULUS,
            Tr("Double integral", "Integral duplo", "Intégrale double", "Doppelintegral", "Integrale doppio", "Integral doble"),
            "∫∫ f(x,y) dy dx",
        ) { line { integral({ case() }, { case() }, "x") { integral({ case() }, { case() }, "y") { } } } },
        Formula(
            "triple_integral", FormulaCategory.CALCULUS,
            Tr("Triple integral", "Integral triplo", "Intégrale triple", "Dreifachintegral", "Integrale triplo", "Integral triple"),
            "∫∫∫ f(x,y,z) dz dy dx",
        ) {
            line {
                integral({ case() }, { case() }, "x") {
                    integral({ case() }, { case() }, "y") { integral({ case() }, { case() }, "z") { } }
                }
            }
        },
        Formula(
            "derivative", FormulaCategory.CALCULUS,
            Tr("Derivative at a point", "Derivada num ponto", "Dérivée en un point", "Ableitung an einer Stelle", "Derivata in un punto", "Derivada en un punto"),
            "d/dx f(x) = k",
        ) { line { derivative("x") { }; eq(); case() } },
        Formula(
            "gaussian", FormulaCategory.CALCULUS,
            Tr("Gaussian integral", "Integral de Gauss", "Intégrale de Gauss", "Gaußsches Integral", "Integrale di Gauss", "Integral de Gauss"),
            "∫ e^(−x²) dx",
        ) { line { integral({ minus(); num("10") }, { num("10") }, "x") { e(); pow { minus(); v("x"); sq() } } } },
        Formula(
            "geometric_series", FormulaCategory.CALCULUS,
            Tr("Geometric series (sum)", "Série geométrica (soma)", "Série géométrique (somme)", "Geometrische Reihe (Summe)", "Serie geometrica (somma)", "Serie geométrica (suma)"),
            "S = a·(1 − rⁿ)/(1 − r)",
        ) { line { slot("S"); eq(); slot("a"); times(); frac({ num("1"); minus(); slot("r"); pow { slot("n") } }, { num("1"); minus(); slot("r") }) } },
        Formula(
            "arithmetic_series", FormulaCategory.CALCULUS,
            Tr("Arithmetic series (sum)", "Série aritmética (soma)", "Série arithmétique (somme)", "Arithmetische Reihe (Summe)", "Serie aritmetica (somma)", "Serie aritmética (suma)"),
            "S = n/2·(2a + (n − 1)·d)",
        ) {
            line {
                slot("S"); eq(); frac({ slot("n") }, { num("2") }); times()
                group { num("2"); slot("a"); plus(); group { slot("n"); minus(); num("1") }; times(); slot("d") }
            }
        },
        // ---------------------------------------------------------------- matrices
        Formula(
            "det2", FormulaCategory.MATRICES,
            Tr("Determinant 2×2", "Determinante 2×2", "Déterminant 2×2", "Determinante 2×2", "Determinante 2×2", "Determinante 2×2"),
            "det [□ □; □ □]",
        ) { line { fn(Fn.DET) { matrix(2, 2) } } },
        Formula(
            "det3", FormulaCategory.MATRICES,
            Tr("Determinant 3×3", "Determinante 3×3", "Déterminant 3×3", "Determinante 3×3", "Determinante 3×3", "Determinante 3×3"),
            "det [3×3]",
        ) { line { fn(Fn.DET) { matrix(3, 3) } } },
        Formula(
            "inverse3", FormulaCategory.MATRICES,
            Tr("Inverse 3×3", "Inversa 3×3", "Inverse 3×3", "Inverse 3×3", "Inversa 3×3", "Inversa 3×3"),
            "A⁻¹",
        ) { line { matrix(3, 3); pow { minus(); num("1") } } },
        Formula(
            "product", FormulaCategory.MATRICES,
            Tr("Matrix product 2×2 · 2×2", "Produto de matrizes 2×2 · 2×2", "Produit de matrices 2×2 · 2×2", "Matrixprodukt 2×2 · 2×2", "Prodotto di matrici 2×2 · 2×2", "Producto de matrices 2×2 · 2×2"),
            "A·B",
        ) { line { matrix(2, 2); times(); matrix(2, 2) } },
        Formula(
            "linear_system", FormulaCategory.MATRICES,
            Tr("Linear system X = A⁻¹·B (3×3)", "Sistema linear X = A⁻¹·B (3×3)", "Système linéaire X = A⁻¹·B (3×3)", "Lineares System X = A⁻¹·B (3×3)", "Sistema lineare X = A⁻¹·B (3×3)", "Sistema lineal X = A⁻¹·B (3×3)"),
            "A⁻¹·B",
        ) { line { matrix(3, 3); pow { minus(); num("1") }; times(); matrix(3, 1) } },
        Formula(
            "eigen2", FormulaCategory.MATRICES,
            Tr("Eigenvalues 2×2 (det(A − λI) = 0)", "Valores próprios 2×2 (det(A − λI) = 0)", "Valeurs propres 2×2 (det(A − λI) = 0)", "Eigenwerte 2×2 (det(A − λI) = 0)", "Autovalori 2×2 (det(A − λI) = 0)", "Valores propios 2×2 (det(A − λI) = 0)"),
            "det(A − λ·I) = 0",
        ) { line { fn(Fn.DET) { matrix(2, 2); minus(); v("λ"); times(); matrix(2, 2, listOf({ num("1") }, { num("0") }, { num("0") }, { num("1") })) }; eq(); num("0") } },
        // ---------------------------------------------------------------- mechanical
        Formula(
            "newton2", FormulaCategory.MECHANICAL,
            Tr("Newton's second law", "2.ª lei de Newton", "Deuxième loi de Newton", "Zweites Newtonsches Gesetz", "Seconda legge di Newton", "Segunda ley de Newton"),
            "F = m·a",
            listOf(FormulaVar("F", "N", "lbf"), FormulaVar("m", "kg", "slug"), FormulaVar("a", "m/s²", "ft/s²")),
        ) { line { slot("F"); eq(); slot("m"); times(); slot("a") } },
        Formula(
            "kinematics", FormulaCategory.MECHANICAL,
            Tr("Uniformly accelerated motion", "Movimento uniformemente acelerado", "Mouvement uniformément accéléré", "Gleichmäßig beschleunigte Bewegung", "Moto uniformemente accelerato", "Movimiento uniformemente acelerado"),
            "s = v₀·t + ½·a·t²",
            listOf(FormulaVar("s", "m", "ft"), FormulaVar("v₀", "m/s", "ft/s"), FormulaVar("t", "s"), FormulaVar("a", "m/s²", "ft/s²")),
        ) { line { slot("s"); eq(); slot("v₀"); times(); slot("t"); plus(); frac({ num("1") }, { num("2") }); slot("a"); times(); slot("t"); sq() } },
        Formula(
            "kinetic", FormulaCategory.MECHANICAL,
            Tr("Kinetic energy", "Energia cinética", "Énergie cinétique", "Kinetische Energie", "Energia cinetica", "Energía cinética"),
            "E = ½·m·v²",
            listOf(FormulaVar("E", "J", "ft·lbf"), FormulaVar("m", "kg", "slug"), FormulaVar("v", "m/s", "ft/s")),
        ) { line { slot("E"); eq(); frac({ num("1") }, { num("2") }); slot("m"); times(); slot("v"); sq() } },
        Formula(
            "potential", FormulaCategory.MECHANICAL,
            Tr("Potential energy", "Energia potencial", "Énergie potentielle", "Potentielle Energie", "Energia potenziale", "Energía potencial"),
            "E = m·g·h",
            listOf(FormulaVar("E", "J", "ft·lbf"), FormulaVar("m", "kg", "slug"), FormulaVar("h", "m", "ft")),
        ) { sys -> line { slot("E"); eq(); slot("m"); times(); num(g(sys)); times(); slot("h") } },
        Formula(
            "hooke", FormulaCategory.MECHANICAL,
            Tr("Hooke's law", "Lei de Hooke", "Loi de Hooke", "Hookesches Gesetz", "Legge di Hooke", "Ley de Hooke"),
            "F = k·x",
            listOf(FormulaVar("F", "N", "lbf"), FormulaVar("k", "N/m", "lbf/in"), FormulaVar("x", "m", "in")),
        ) { line { slot("F"); eq(); slot("k"); times(); slot("x") } },
        Formula(
            "rot_power", FormulaCategory.MECHANICAL,
            Tr("Rotational power", "Potência rotacional", "Puissance de rotation", "Drehleistung", "Potenza di rotazione", "Potencia rotacional"),
            "P = T·ω",
            listOf(FormulaVar("P", "W", "ft·lbf/s"), FormulaVar("T", "N·m", "ft·lbf"), FormulaVar("ω", "rad/s")),
        ) { line { slot("P"); eq(); slot("T"); times(); slot("ω") } },
        // ---------------------------------------------------------------- elasticity
        Formula(
            "stress", FormulaCategory.ELASTICITY,
            Tr("Normal stress", "Tensão normal", "Contrainte normale", "Normalspannung", "Tensione normale", "Esfuerzo normal"),
            "σ = F/A",
            listOf(FormulaVar("σ", "Pa", "psi"), FormulaVar("F", "N", "lbf"), FormulaVar("A", "m²", "in²")),
        ) { line { slot("σ"); eq(); frac({ slot("F") }, { slot("A") }) } },
        Formula(
            "strain", FormulaCategory.ELASTICITY,
            Tr("Normal strain", "Extensão (deformação normal)", "Déformation normale", "Dehnung", "Deformazione normale", "Deformación unitaria"),
            "ε = ΔL/L₀",
            listOf(FormulaVar("ΔL", "m", "in"), FormulaVar("L₀", "m", "in")),
        ) { line { slot("ε"); eq(); frac({ slot("ΔL") }, { slot("L₀") }) } },
        Formula(
            "hooke_stress", FormulaCategory.ELASTICITY,
            Tr("Hooke's law (stress–strain)", "Lei de Hooke (tensão–deformação)", "Loi de Hooke (contrainte–déformation)", "Hookesches Gesetz (Spannung–Dehnung)", "Legge di Hooke (tensione–deformazione)", "Ley de Hooke (tensión–deformación)"),
            "σ = E·ε",
            listOf(FormulaVar("σ", "Pa", "psi"), FormulaVar("E", "Pa", "psi")),
        ) { line { slot("σ"); eq(); slot("E"); times(); slot("ε") } },
        Formula(
            "shear_hooke", FormulaCategory.ELASTICITY,
            Tr("Hooke's law in shear", "Lei de Hooke ao corte", "Loi de Hooke en cisaillement", "Hookesches Gesetz für Schub", "Legge di Hooke a taglio", "Ley de Hooke a cortante"),
            "τ = G·γ",
            listOf(FormulaVar("τ", "Pa", "psi"), FormulaVar("G", "Pa", "psi"), FormulaVar("γ", "rad")),
        ) { line { slot("τ"); eq(); slot("G"); times(); slot("γ") } },
        Formula(
            "poisson", FormulaCategory.ELASTICITY,
            Tr("Poisson's ratio", "Coeficiente de Poisson", "Coefficient de Poisson", "Querkontraktionszahl", "Coefficiente di Poisson", "Coeficiente de Poisson"),
            "ν = −εₜ/εₗ",
        ) { line { slot("ν"); eq(); minus(); frac({ slot("εₜ") }, { slot("εₗ") }) } },
        Formula(
            "shear_modulus", FormulaCategory.ELASTICITY,
            Tr("Shear modulus from E and ν", "Módulo de distorção (E, ν)", "Module de cisaillement (E, ν)", "Schubmodul aus E und ν", "Modulo di taglio (E, ν)", "Módulo de cortante (E, ν)"),
            "G = E/(2(1 + ν))",
            listOf(FormulaVar("G", "Pa", "psi"), FormulaVar("E", "Pa", "psi")),
        ) { line { slot("G"); eq(); frac({ slot("E") }, { num("2"); group { num("1"); plus(); slot("ν") } }) } },
        Formula(
            "bulk_modulus", FormulaCategory.ELASTICITY,
            Tr("Bulk modulus from E and ν", "Módulo de compressibilidade (E, ν)", "Module de compressibilité (E, ν)", "Kompressionsmodul aus E und ν", "Modulo di compressibilità (E, ν)", "Módulo de compresibilidad (E, ν)"),
            "K = E/(3(1 − 2ν))",
            listOf(FormulaVar("K", "Pa", "psi"), FormulaVar("E", "Pa", "psi")),
        ) { line { slot("K"); eq(); frac({ slot("E") }, { num("3"); group { num("1"); minus(); num("2"); slot("ν") } }) } },
        Formula(
            "lame", FormulaCategory.ELASTICITY,
            Tr("Lamé's first parameter", "Primeiro parâmetro de Lamé", "Premier coefficient de Lamé", "Erste Lamé-Konstante", "Primo parametro di Lamé", "Primer parámetro de Lamé"),
            "λ = E·ν/((1 + ν)(1 − 2ν))",
            listOf(FormulaVar("λ", "Pa", "psi"), FormulaVar("E", "Pa", "psi")),
        ) { line { slot("λ"); eq(); frac({ slot("E"); times(); slot("ν") }, { group { num("1"); plus(); slot("ν") }; group { num("1"); minus(); num("2"); slot("ν") } }) } },
        Formula(
            "hooke_3d", FormulaCategory.ELASTICITY,
            Tr("Generalised Hooke's law (3D)", "Lei de Hooke generalizada (3D)", "Loi de Hooke généralisée (3D)", "Verallgemeinertes Hookesches Gesetz (3D)", "Legge di Hooke generalizzata (3D)", "Ley de Hooke generalizada (3D)"),
            "εx = (σx − ν(σy + σz))/E",
            listOf(FormulaVar("σ", "Pa", "psi"), FormulaVar("E", "Pa", "psi")),
        ) { line { slot("ε_x"); eq(); frac({ slot("σ_x"); minus(); slot("ν"); group { slot("σ_y"); plus(); slot("σ_z") } }, { slot("E") }) } },
        Formula(
            "thermal_strain", FormulaCategory.ELASTICITY,
            Tr("Thermal strain", "Deformação térmica", "Déformation thermique", "Wärmedehnung", "Deformazione termica", "Deformación térmica"),
            "ε = α·ΔT",
            listOf(FormulaVar("α", "1/K", "1/°F"), FormulaVar("ΔT", "K", "°F")),
        ) { line { slot("ε"); eq(); slot("α"); times(); slot("ΔT") } },
        Formula(
            "thermal_stress", FormulaCategory.ELASTICITY,
            Tr("Thermal stress (restrained bar)", "Tensão térmica (barra impedida)", "Contrainte thermique (barre bridée)", "Wärmespannung (eingespannter Stab)", "Tensione termica (barra vincolata)", "Tensión térmica (barra empotrada)"),
            "σ = E·α·ΔT",
            listOf(FormulaVar("σ", "Pa", "psi"), FormulaVar("E", "Pa", "psi"), FormulaVar("α", "1/K", "1/°F"), FormulaVar("ΔT", "K", "°F")),
        ) { line { slot("σ"); eq(); slot("E"); times(); slot("α"); times(); slot("ΔT") } },
        Formula(
            "axial_deformation", FormulaCategory.ELASTICITY,
            Tr("Axial deformation of a bar", "Deformação axial de uma barra", "Allongement d'une barre", "Längenänderung eines Stabes", "Allungamento di una barra", "Deformación axial de una barra"),
            "δ = F·L/(A·E)",
            listOf(FormulaVar("δ", "m", "in"), FormulaVar("F", "N", "lbf"), FormulaVar("L", "m", "in"), FormulaVar("A", "m²", "in²"), FormulaVar("E", "Pa", "psi")),
        ) { line { slot("δ"); eq(); frac({ slot("F"); times(); slot("L") }, { slot("A"); times(); slot("E") }) } },
        Formula(
            "strain_energy", FormulaCategory.ELASTICITY,
            Tr("Strain energy density", "Densidade de energia de deformação", "Densité d'énergie de déformation", "Formänderungsenergiedichte", "Densità di energia di deformazione", "Densidad de energía de deformación"),
            "u = σ²/(2E)",
            listOf(FormulaVar("u", "J/m³", "psi"), FormulaVar("σ", "Pa", "psi"), FormulaVar("E", "Pa", "psi")),
        ) { line { slot("u"); eq(); frac({ slot("σ"); sq() }, { num("2"); slot("E") }) } },
        // ---------------------------------------------------------------- beams
        Formula(
            "i_rect", FormulaCategory.BEAMS,
            Tr("Second moment of area, rectangle", "Momento de inércia, retângulo", "Moment quadratique, rectangle", "Flächenträgheitsmoment, Rechteck", "Momento d'inerzia, rettangolo", "Momento de inercia, rectángulo"),
            "I = b·h³/12",
            listOf(FormulaVar("I", "m⁴", "in⁴"), FormulaVar("b, h", "m", "in")),
        ) { line { slot("I"); eq(); frac({ slot("b"); times(); slot("h"); pow { num("3") } }, { num("12") }) } },
        Formula(
            "i_circle", FormulaCategory.BEAMS,
            Tr("Second moment of area, solid circle", "Momento de inércia, círculo", "Moment quadratique, disque", "Flächenträgheitsmoment, Kreis", "Momento d'inerzia, cerchio", "Momento de inercia, círculo"),
            "I = π·d⁴/64",
            listOf(FormulaVar("I", "m⁴", "in⁴"), FormulaVar("d", "m", "in")),
        ) { line { slot("I"); eq(); frac({ pi(); slot("d"); pow { num("4") } }, { num("64") }) } },
        Formula(
            "j_circle", FormulaCategory.BEAMS,
            Tr("Polar moment of area, solid shaft", "Momento polar de inércia, veio maciço", "Moment polaire, arbre plein", "Polares Flächenträgheitsmoment, Vollwelle", "Momento polare, albero pieno", "Momento polar, eje macizo"),
            "J = π·d⁴/32",
            listOf(FormulaVar("J", "m⁴", "in⁴"), FormulaVar("d", "m", "in")),
        ) { line { slot("J"); eq(); frac({ pi(); slot("d"); pow { num("4") } }, { num("32") }) } },
        Formula(
            "bending_stress", FormulaCategory.BEAMS,
            Tr("Bending stress (Navier)", "Tensão de flexão (Navier)", "Contrainte de flexion (Navier)", "Biegespannung (Navier)", "Tensione di flessione (Navier)", "Tensión de flexión (Navier)"),
            "σ = M·y/I",
            listOf(FormulaVar("σ", "Pa", "psi"), FormulaVar("M", "N·m", "lbf·in"), FormulaVar("y", "m", "in"), FormulaVar("I", "m⁴", "in⁴")),
        ) { line { slot("σ"); eq(); frac({ slot("M"); times(); slot("y") }, { slot("I") }) } },
        Formula(
            "cantilever", FormulaCategory.BEAMS,
            Tr("Cantilever tip deflection (end load)", "Flecha de consola (carga na ponta)", "Flèche d'une console (charge en bout)", "Kragträger-Durchbiegung (Endlast)", "Freccia di una mensola (carico in punta)", "Flecha de voladizo (carga en el extremo)"),
            "δ = F·L³/(3E·I)",
            listOf(FormulaVar("δ", "m", "in"), FormulaVar("F", "N", "lbf"), FormulaVar("L", "m", "in"), FormulaVar("E", "Pa", "psi"), FormulaVar("I", "m⁴", "in⁴")),
        ) { line { slot("δ"); eq(); frac({ slot("F"); times(); slot("L"); pow { num("3") } }, { num("3"); slot("E"); times(); slot("I") }) } },
        Formula(
            "simply_supported", FormulaCategory.BEAMS,
            Tr("Simply supported beam, central load", "Viga simplesmente apoiada, carga a meio vão", "Poutre sur deux appuis, charge centrée", "Einfeldträger, Einzellast in Feldmitte", "Trave appoggiata, carico in mezzeria", "Viga simplemente apoyada, carga central"),
            "δ = F·L³/(48E·I)",
            listOf(FormulaVar("δ", "m", "in"), FormulaVar("F", "N", "lbf"), FormulaVar("L", "m", "in"), FormulaVar("E", "Pa", "psi"), FormulaVar("I", "m⁴", "in⁴")),
        ) { line { slot("δ"); eq(); frac({ slot("F"); times(); slot("L"); pow { num("3") } }, { num("48"); slot("E"); times(); slot("I") }) } },
        Formula(
            "udl_beam", FormulaCategory.BEAMS,
            Tr("Simply supported beam, uniform load", "Viga simplesmente apoiada, carga uniforme", "Poutre sur deux appuis, charge répartie", "Einfeldträger, Streckenlast", "Trave appoggiata, carico distribuito", "Viga simplemente apoyada, carga uniforme"),
            "δ = 5w·L⁴/(384E·I)",
            listOf(FormulaVar("δ", "m", "in"), FormulaVar("w", "N/m", "lbf/in"), FormulaVar("L", "m", "in"), FormulaVar("E", "Pa", "psi"), FormulaVar("I", "m⁴", "in⁴")),
        ) { line { slot("δ"); eq(); frac({ num("5"); slot("w"); times(); slot("L"); pow { num("4") } }, { num("384"); slot("E"); times(); slot("I") }) } },
        Formula(
            "torsion_stress", FormulaCategory.BEAMS,
            Tr("Torsional shear stress", "Tensão de corte por torção", "Contrainte de torsion", "Torsionsspannung", "Tensione tangenziale da torsione", "Tensión cortante por torsión"),
            "τ = T·r/J",
            listOf(FormulaVar("τ", "Pa", "psi"), FormulaVar("T", "N·m", "lbf·in"), FormulaVar("r", "m", "in"), FormulaVar("J", "m⁴", "in⁴")),
        ) { line { slot("τ"); eq(); frac({ slot("T"); times(); slot("r") }, { slot("J") }) } },
        Formula(
            "twist_angle", FormulaCategory.BEAMS,
            Tr("Angle of twist", "Ângulo de torção", "Angle de torsion", "Verdrehwinkel", "Angolo di torsione", "Ángulo de torsión"),
            "θ = T·L/(G·J)",
            listOf(FormulaVar("θ", "rad"), FormulaVar("T", "N·m", "lbf·in"), FormulaVar("L", "m", "in"), FormulaVar("G", "Pa", "psi"), FormulaVar("J", "m⁴", "in⁴")),
        ) { line { slot("θ"); eq(); frac({ slot("T"); times(); slot("L") }, { slot("G"); times(); slot("J") }) } },
        Formula(
            "euler_buckling", FormulaCategory.BEAMS,
            Tr("Euler buckling load", "Carga crítica de Euler (encurvadura)", "Charge critique d'Euler (flambement)", "Euler-Knicklast", "Carico critico di Eulero", "Carga crítica de Euler (pandeo)"),
            "P = π²·E·I/(K·L)²",
            listOf(FormulaVar("P", "N", "lbf"), FormulaVar("E", "Pa", "psi"), FormulaVar("I", "m⁴", "in⁴"), FormulaVar("L", "m", "in")),
        ) { line { slot("P"); eq(); frac({ pi(); sq(); slot("E"); times(); slot("I") }, { group { slot("K"); times(); slot("L") }; sq() }) } },
        // ---------------------------------------------------------------- stress state
        Formula(
            "principal_1", FormulaCategory.STRESS_STATE,
            Tr("Principal stress σ₁ (plane stress)", "Tensão principal σ₁ (estado plano)", "Contrainte principale σ₁ (plane)", "Hauptspannung σ₁ (eben)", "Tensione principale σ₁ (piana)", "Tensión principal σ₁ (plana)"),
            "σ₁ = (σx + σy)/2 + √(((σx − σy)/2)² + τxy²)",
            listOf(FormulaVar("σ, τ", "Pa", "psi")),
        ) {
            line {
                slot("σ₁"); eq(); frac({ slot("σ_x"); plus(); slot("σ_y") }, { num("2") }); plus()
                sqrt { group { frac({ slot("σ_x"); minus(); slot("σ_y") }, { num("2") }) }; sq(); plus(); slot("τ_xy"); sq() }
            }
        },
        Formula(
            "principal_2", FormulaCategory.STRESS_STATE,
            Tr("Principal stress σ₂ (plane stress)", "Tensão principal σ₂ (estado plano)", "Contrainte principale σ₂ (plane)", "Hauptspannung σ₂ (eben)", "Tensione principale σ₂ (piana)", "Tensión principal σ₂ (plana)"),
            "σ₂ = (σx + σy)/2 − √(((σx − σy)/2)² + τxy²)",
            listOf(FormulaVar("σ, τ", "Pa", "psi")),
        ) {
            line {
                slot("σ₂"); eq(); frac({ slot("σ_x"); plus(); slot("σ_y") }, { num("2") }); minus()
                sqrt { group { frac({ slot("σ_x"); minus(); slot("σ_y") }, { num("2") }) }; sq(); plus(); slot("τ_xy"); sq() }
            }
        },
        Formula(
            "max_shear", FormulaCategory.STRESS_STATE,
            Tr("Maximum in-plane shear stress", "Tensão de corte máxima no plano", "Cisaillement maximal dans le plan", "Maximale Schubspannung (eben)", "Massima tensione tangenziale nel piano", "Tensión cortante máxima en el plano"),
            "τmax = √(((σx − σy)/2)² + τxy²)",
            listOf(FormulaVar("σ, τ", "Pa", "psi")),
        ) { line { slot("τ_max"); eq(); sqrt { group { frac({ slot("σ_x"); minus(); slot("σ_y") }, { num("2") }) }; sq(); plus(); slot("τ_xy"); sq() } } },
        Formula(
            "von_mises", FormulaCategory.STRESS_STATE,
            Tr("Von Mises stress (plane stress)", "Tensão de von Mises (estado plano)", "Contrainte de von Mises (plane)", "Von-Mises-Vergleichsspannung (eben)", "Tensione di von Mises (piana)", "Tensión de von Mises (plana)"),
            "σvm = √(σx² − σx·σy + σy² + 3τxy²)",
            listOf(FormulaVar("σ, τ", "Pa", "psi")),
        ) { line { slot("σ_vm"); eq(); sqrt { slot("σ_x"); sq(); minus(); slot("σ_x"); times(); slot("σ_y"); plus(); slot("σ_y"); sq(); plus(); num("3"); slot("τ_xy"); sq() } } },
        Formula(
            "tresca", FormulaCategory.STRESS_STATE,
            Tr("Tresca equivalent stress", "Tensão equivalente de Tresca", "Contrainte équivalente de Tresca", "Tresca-Vergleichsspannung", "Tensione equivalente di Tresca", "Tensión equivalente de Tresca"),
            "σT = σ₁ − σ₃",
            listOf(FormulaVar("σ", "Pa", "psi")),
        ) { line { slot("σ_T"); eq(); slot("σ₁"); minus(); slot("σ₃") } },
        Formula(
            "safety_factor", FormulaCategory.STRESS_STATE,
            Tr("Safety factor", "Coeficiente de segurança", "Coefficient de sécurité", "Sicherheitsfaktor", "Coefficiente di sicurezza", "Factor de seguridad"),
            "n = σy/σ",
            listOf(FormulaVar("σ", "Pa", "psi")),
        ) { line { slot("n"); eq(); frac({ slot("σ_y") }, { slot("σ") }) } },
        // ---------------------------------------------------------------- fluids
        Formula(
            "hydrostatic", FormulaCategory.FLUIDS,
            Tr("Hydrostatic pressure", "Pressão hidrostática", "Pression hydrostatique", "Hydrostatischer Druck", "Pressione idrostatica", "Presión hidrostática"),
            "p = ρ·g·h",
            listOf(FormulaVar("p", "Pa", "lbf/ft²"), FormulaVar("ρ", "kg/m³", "slug/ft³"), FormulaVar("h", "m", "ft")),
        ) { sys -> line { slot("p"); eq(); slot("ρ"); times(); num(g(sys)); times(); slot("h") } },
        Formula(
            "continuity", FormulaCategory.FLUIDS,
            Tr("Flow rate (continuity)", "Caudal (continuidade)", "Débit (continuité)", "Volumenstrom (Kontinuität)", "Portata (continuità)", "Caudal (continuidad)"),
            "Q = A·v",
            listOf(FormulaVar("Q", "m³/s", "ft³/s"), FormulaVar("A", "m²", "ft²"), FormulaVar("v", "m/s", "ft/s")),
        ) { line { slot("Q"); eq(); slot("A"); times(); slot("v") } },
        Formula(
            "reynolds", FormulaCategory.FLUIDS,
            Tr("Reynolds number", "Número de Reynolds", "Nombre de Reynolds", "Reynolds-Zahl", "Numero di Reynolds", "Número de Reynolds"),
            "Re = ρ·v·D/μ",
            listOf(FormulaVar("ρ", "kg/m³", "slug/ft³"), FormulaVar("v", "m/s", "ft/s"), FormulaVar("D", "m", "ft"), FormulaVar("μ", "Pa·s", "lbf·s/ft²")),
        ) { line { slot("Re"); eq(); frac({ slot("ρ"); times(); slot("v"); times(); slot("D") }, { slot("μ") }) } },
        Formula(
            "bernoulli", FormulaCategory.FLUIDS,
            Tr("Bernoulli's equation", "Equação de Bernoulli", "Équation de Bernoulli", "Bernoulli-Gleichung", "Equazione di Bernoulli", "Ecuación de Bernoulli"),
            "p₁ + ½ρv₁² + ρgh₁ = p₂ + ½ρv₂² + ρgh₂",
            listOf(FormulaVar("p", "Pa", "lbf/ft²"), FormulaVar("ρ", "kg/m³", "slug/ft³"), FormulaVar("v", "m/s", "ft/s"), FormulaVar("h", "m", "ft")),
        ) { sys ->
            line {
                slot("p₁"); plus(); frac({ num("1") }, { num("2") }); slot("ρ"); slot("v₁"); sq(); plus(); slot("ρ"); times(); num(g(sys)); times(); slot("h₁")
                eq()
                slot("p₂"); plus(); frac({ num("1") }, { num("2") }); slot("ρ"); slot("v₂"); sq(); plus(); slot("ρ"); times(); num(g(sys)); times(); slot("h₂")
            }
        },
        Formula(
            "ideal_gas", FormulaCategory.FLUIDS,
            Tr("Ideal gas law", "Lei dos gases ideais", "Loi des gaz parfaits", "Ideales Gasgesetz", "Legge dei gas perfetti", "Ley de los gases ideales"),
            "p·V = n·R·T",
            listOf(FormulaVar("p", "Pa"), FormulaVar("V", "m³"), FormulaVar("n", "mol"), FormulaVar("T", "K")),
        ) { line { slot("p"); times(); slot("V"); eq(); slot("n"); times(); num("8.314462618"); times(); slot("T") } },
        // ---------------------------------------------------------------- electro
        Formula(
            "ohm", FormulaCategory.ELECTRO,
            Tr("Ohm's law", "Lei de Ohm", "Loi d'Ohm", "Ohmsches Gesetz", "Legge di Ohm", "Ley de Ohm"),
            "V = I·R",
            listOf(FormulaVar("V", "V"), FormulaVar("I", "A"), FormulaVar("R", "Ω")),
        ) { line { slot("V"); eq(); slot("I"); times(); slot("R") } },
        Formula(
            "elec_power", FormulaCategory.ELECTRO,
            Tr("Electric power", "Potência elétrica", "Puissance électrique", "Elektrische Leistung", "Potenza elettrica", "Potencia eléctrica"),
            "P = V·I",
            listOf(FormulaVar("P", "W"), FormulaVar("V", "V"), FormulaVar("I", "A")),
        ) { line { slot("P"); eq(); slot("V"); times(); slot("I") } },
        Formula(
            "parallel_r", FormulaCategory.ELECTRO,
            Tr("Resistors in parallel", "Resistências em paralelo", "Résistances en parallèle", "Parallelschaltung von Widerständen", "Resistenze in parallelo", "Resistencias en paralelo"),
            "R = 1/(1/R₁ + 1/R₂)",
            listOf(FormulaVar("R", "Ω")),
        ) { line { slot("R"); eq(); frac({ num("1") }, { frac({ num("1") }, { slot("R₁") }); plus(); frac({ num("1") }, { slot("R₂") }) }) } },
        Formula(
            "cap_energy", FormulaCategory.ELECTRO,
            Tr("Energy in a capacitor", "Energia num condensador", "Énergie d'un condensateur", "Energie im Kondensator", "Energia in un condensatore", "Energía en un condensador"),
            "E = ½·C·V²",
            listOf(FormulaVar("E", "J"), FormulaVar("C", "F"), FormulaVar("V", "V")),
        ) { line { slot("E"); eq(); frac({ num("1") }, { num("2") }); slot("C"); times(); slot("V"); sq() } },
        Formula(
            "rc", FormulaCategory.ELECTRO,
            Tr("RC time constant", "Constante de tempo RC", "Constante de temps RC", "RC-Zeitkonstante", "Costante di tempo RC", "Constante de tiempo RC"),
            "τ = R·C",
            listOf(FormulaVar("τ", "s"), FormulaVar("R", "Ω"), FormulaVar("C", "F")),
        ) { line { slot("τ"); eq(); slot("R"); times(); slot("C") } },
        Formula(
            "three_phase", FormulaCategory.ELECTRO,
            Tr("Three-phase power", "Potência trifásica", "Puissance triphasée", "Drehstromleistung", "Potenza trifase", "Potencia trifásica"),
            "P = √3·V·I·cos φ",
            listOf(FormulaVar("P", "W"), FormulaVar("V", "V"), FormulaVar("I", "A")),
        ) { line { slot("P"); eq(); sqrt { num("3") }; times(); slot("V"); times(); slot("I"); times(); fn(Fn.COS) { slot("φ") } } },
        Formula(
            "resonance", FormulaCategory.ELECTRO,
            Tr("LC resonant frequency", "Frequência de ressonância LC", "Fréquence de résonance LC", "LC-Resonanzfrequenz", "Frequenza di risonanza LC", "Frecuencia de resonancia LC"),
            "f = 1/(2π·√(L·C))",
            listOf(FormulaVar("f", "Hz"), FormulaVar("L", "H"), FormulaVar("C", "F")),
        ) { line { slot("f"); eq(); frac({ num("1") }, { num("2"); pi(); sqrt { slot("L"); times(); slot("C") } }) } },
        // ---------------------------------------------------------------- computer
        Formula(
            "transfer_time", FormulaCategory.COMPUTER,
            Tr("Transfer time", "Tempo de transferência", "Temps de transfert", "Übertragungszeit", "Tempo di trasferimento", "Tiempo de transferencia"),
            "t = 8·S/B",
            listOf(FormulaVar("t", "s"), FormulaVar("S", "MB"), FormulaVar("B", "Mbit/s")),
        ) { line { slot("t"); eq(); frac({ num("8"); slot("S") }, { slot("B") }) } },
        Formula(
            "bit_values", FormulaCategory.COMPUTER,
            Tr("Values with n bits", "Valores com n bits", "Valeurs sur n bits", "Werte mit n Bits", "Valori con n bit", "Valores con n bits"),
            "N = 2ⁿ",
        ) { line { slot("N"); eq(); num("2"); pow { slot("n") } } },
        Formula(
            "amdahl", FormulaCategory.COMPUTER,
            Tr("Amdahl's law", "Lei de Amdahl", "Loi d'Amdahl", "Amdahlsches Gesetz", "Legge di Amdahl", "Ley de Amdahl"),
            "S = 1/((1 − p) + p/n)",
        ) { line { slot("S"); eq(); frac({ num("1") }, { group { num("1"); minus(); slot("p") }; plus(); frac({ slot("p") }, { slot("n") }) }) } },
        Formula(
            "shannon", FormulaCategory.COMPUTER,
            Tr("Shannon capacity", "Capacidade de Shannon", "Capacité de Shannon", "Shannon-Kapazität", "Capacità di Shannon", "Capacidad de Shannon"),
            "C = B·log₂(1 + SNR)",
            listOf(FormulaVar("C", "bit/s"), FormulaVar("B", "Hz")),
        ) { line { slot("C"); eq(); slot("B"); times(); frac({ fn(Fn.LN) { num("1"); plus(); slot("SNR") } }, { fn(Fn.LN) { num("2") } }) } },
    )

    fun byCategory(c: FormulaCategory) = all.filter { it.category == c }
}
