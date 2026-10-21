package com.arturpelicho.calcexpress.core

/** Text in the app's languages: en (default), pt, fr, de, it, es. */
data class Tr(val en: String, val pt: String, val fr: String, val de: String, val it: String, val es: String) {
    fun get(lang: String) = when (lang) {
        "pt" -> pt; "fr" -> fr; "de" -> de; "it" -> it; "es" -> es; else -> en
    }
}

enum class FormulaCategory(val title: Tr) {
    GENERAL(Tr("General maths", "Matemática geral", "Mathématiques générales", "Allgemeine Mathematik", "Matematica generale", "Matemáticas generales")),
    CALCULUS(Tr("Calculus & series", "Cálculo e séries", "Analyse et séries", "Analysis & Reihen", "Analisi e serie", "Cálculo y series")),
    MECHANICAL(Tr("Mechanical engineering", "Engenharia mecânica", "Génie mécanique", "Maschinenbau", "Ingegneria meccanica", "Ingeniería mecánica")),
    COMPUTER(Tr("Computer engineering", "Engenharia informática", "Informatique", "Informatik", "Ingegneria informatica", "Ingeniería informática")),
    FLUIDS(Tr("Fluids", "Fluidos", "Fluides", "Fluide", "Fluidi", "Fluidos")),
    ELECTRO(Tr("Electrotechnics", "Eletrotecnia", "Électrotechnique", "Elektrotechnik", "Elettrotecnica", "Electrotecnia")),
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
)

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
        // ---------------------------------------------------------------- mechanical
        Formula(
            "newton2", FormulaCategory.MECHANICAL,
            Tr("Newton's second law", "2.ª lei de Newton", "Deuxième loi de Newton", "Zweites Newtonsches Gesetz", "Seconda legge di Newton", "Segunda ley de Newton"),
            "F = m·a",
            listOf(FormulaVar("F", "N", "lbf"), FormulaVar("m", "kg", "slug"), FormulaVar("a", "m/s²", "ft/s²")),
        ) { line { slot("F"); eq(); slot("m"); times(); slot("a") } },
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
            "kinematics", FormulaCategory.MECHANICAL,
            Tr("Uniformly accelerated motion", "Movimento uniformemente acelerado", "Mouvement uniformément accéléré", "Gleichmäßig beschleunigte Bewegung", "Moto uniformemente accelerato", "Movimiento uniformemente acelerado"),
            "s = v₀·t + ½·a·t²",
            listOf(FormulaVar("s", "m", "ft"), FormulaVar("v₀", "m/s", "ft/s"), FormulaVar("t", "s"), FormulaVar("a", "m/s²", "ft/s²")),
        ) { line { slot("s"); eq(); slot("v₀"); times(); slot("t"); plus(); frac({ num("1") }, { num("2") }); slot("a"); times(); slot("t"); sq() } },
        Formula(
            "stress", FormulaCategory.MECHANICAL,
            Tr("Normal stress", "Tensão normal", "Contrainte normale", "Normalspannung", "Tensione normale", "Esfuerzo normal"),
            "σ = F/A",
            listOf(FormulaVar("σ", "Pa", "psi"), FormulaVar("F", "N", "lbf"), FormulaVar("A", "m²", "in²")),
        ) { line { slot("σ"); eq(); frac({ slot("F") }, { slot("A") }) } },
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
        // ---------------------------------------------------------------- electrotechnics
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
    )

    fun byCategory(c: FormulaCategory) = all.filter { it.category == c }
}
