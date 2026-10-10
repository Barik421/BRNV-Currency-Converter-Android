package com.brnv.currencyconverter.domain

import java.util.Currency
import java.util.Locale

data class CurrencyInfo(
    val code: String,
    val englishName: String,
    val ukrainianName: String,
    val flag: String,
    val aliases: String,
) {
    val symbol: String get() = runCatching {
        Currency.getInstance(code).getSymbol(Locale.getDefault())
    }.getOrDefault(code)
    fun name(locale: Locale): String = if (locale.language == "uk") ukrainianName else englishName
    fun matches(query: String): Boolean =
        listOf(code, englishName, ukrainianName, aliases).any { it.contains(query.trim(), ignoreCase = true) }
}

/** Fiat catalog and translations from brnvconverter.com/script.js, reviewed 2026-10-10. */
object CurrencyCatalog {
    val all = listOf(
        CurrencyInfo("USD", "US Dollar", "Долар США", "🇺🇸", "долар доллар долар сша доллар сша американський долар американский доллар united states dollar us dollar"),
        CurrencyInfo("EUR", "Euro", "Євро", "🇪🇺", "євро евро european euro європейська валюта европейская валюта"),
        CurrencyInfo("GBP", "British Pound", "Британський фунт", "🇬🇧", "фунт британський фунт британский фунт фунт стерлінгів фунт стерлингов pound sterling"),
        CurrencyInfo("UAH", "Ukrainian Hryvnia", "Українська гривня", "🇺🇦", "гривня гривна українська гривня украинская гривна hryvnia ukraine"),
        CurrencyInfo("PLN", "Polish Zloty", "Польський злотий", "🇵🇱", "злотий злотый польський злотий польский злотый zloty poland"),
        CurrencyInfo("HUF", "Hungarian Forint", "Угорський форинт", "🇭🇺", "форинт форінт флоринт флорент угорський форинт венгерский форинт forint florin hungary budapest будапешт"),
        CurrencyInfo("RON", "Romanian Leu", "Румунський лей", "🇷🇴", "лей леї румунський лей румынский лей leu romania"),
        CurrencyInfo("BGN", "Bulgarian Lev", "Болгарський лев", "🇧🇬", "лев болгарський лев болгарский лев lev bulgaria"),
        CurrencyInfo("CZK", "Czech Koruna", "Чеська крона", "🇨🇿", "крона чеська крона чешская крона koruna czech"),
        CurrencyInfo("SEK", "Swedish Krona", "Шведська крона", "🇸🇪", "крона шведська крона шведская крона krona sweden"),
        CurrencyInfo("NOK", "Norwegian Krone", "Норвезька крона", "🇳🇴", "крона норвезька крона норвежская крона krone norway"),
        CurrencyInfo("DKK", "Danish Krone", "Данська крона", "🇩🇰", "крона данська крона датская крона krone denmark"),
        CurrencyInfo("CHF", "Swiss Franc", "Швейцарський франк", "🇨🇭", "франк швейцарський франк швейцарский франк swiss franc switzerland"),
        CurrencyInfo("ISK", "Icelandic Krona", "Ісландська крона", "🇮🇸", "ісландська крона исландская крона krona iceland"),
        CurrencyInfo("RSD", "Serbian Dinar", "Сербський динар", "🇷🇸", "динар сербський динар сербский динар dinar serbia"),
        CurrencyInfo("GEL", "Georgian Lari", "Грузинський ларі", "🇬🇪", "ларі лари грузинський ларі грузинский лари lari georgia"),
        CurrencyInfo("MDL", "Moldovan Leu", "Молдовський лей", "🇲🇩", "молдовський лей молдавский лей лей молдова leu moldova"),
        CurrencyInfo("ALL", "Albanian Lek", "Албанський лек", "🇦🇱", "лек албанський лек албанский лек lek albania"),
        CurrencyInfo("BAM", "Bosnia-Herzegovina Convertible Mark", "Боснійська конвертована марка", "🇧🇦", "конвертована марка боснійська марка боснийская марка mark bosnia"),
        CurrencyInfo("MKD", "Macedonian Denar", "Македонський денар", "🇲🇰", "денар македонський денар македонский денар denar macedonia"),
        CurrencyInfo("TRY", "Turkish Lira", "Турецька ліра", "🇹🇷", "ліра лира турецька ліра турецкая лира lira turkey"),
        CurrencyInfo("CAD", "Canadian Dollar", "Канадський долар", "🇨🇦", "канадський долар канадский доллар долар канада доллар канада canadian dollar"),
        CurrencyInfo("AUD", "Australian Dollar", "Австралійський долар", "🇦🇺", "австралійський долар австралийский доллар долар австралія доллар австралия australian dollar"),
        CurrencyInfo("NZD", "New Zealand Dollar", "Новозеландський долар", "🇳🇿", "новозеландський долар новозеландский доллар долар нова зеландія dollar new zealand"),
        CurrencyInfo("JPY", "Japanese Yen", "Японська єна", "🇯🇵", "єна иена японська єна японская иена yen japan"),
        CurrencyInfo("CNY", "Chinese Yuan", "Китайський юань", "🇨🇳", "юань китайський юань китайский юань yuan china"),
        CurrencyInfo("HKD", "Hong Kong Dollar", "Гонконгський долар", "🇭🇰", "гонконгський долар гонконгский доллар долар гонконг hong kong dollar"),
        CurrencyInfo("SGD", "Singapore Dollar", "Сингапурський долар", "🇸🇬", "сингапурський долар сингапурский доллар долар сингапур singapore dollar"),
        CurrencyInfo("AED", "UAE Dirham", "Дирхам ОАЕ", "🇦🇪", "дирхам дірхам дирхам оае дирхам оаэ дирхам емірати дирхам эмираты dirham uae emirates"),
        CurrencyInfo("BYN", "Belarusian Ruble", "Білоруський рубль", "🇧🇾", "білоруський рубль белорусский рубль рубль беларусь belarusian ruble belarus"),
        CurrencyInfo("KZT", "Kazakhstani Tenge", "Казахстанський тенге", "🇰🇿", "тенге казахстанський тенге казахстанский тенге tenge kazakhstan"),
        CurrencyInfo("UZS", "Uzbekistani Som", "Узбецький сум", "🇺🇿", "сум узбецький сум узбекский сум som uzbekistan"),
        CurrencyInfo("AMD", "Armenian Dram", "Вірменський драм", "🇦🇲", "драм вірменський драм армянский драм dram armenia"),
        CurrencyInfo("AZN", "Azerbaijani Manat", "Азербайджанський манат", "🇦🇿", "манат азербайджанський манат азербайджанский манат manat azerbaijan"),
        CurrencyInfo("EGP", "Egyptian Pound", "Єгипетський фунт", "🇪🇬", "єгипетський фунт египетский фунт фунт єгипет фунт египет egyptian pound egypt"),
        CurrencyInfo("MAD", "Moroccan Dirham", "Марокканський дирхам", "🇲🇦", "марокканський дирхам марокканский дирхам дирхам марокко moroccan dirham morocco"),
        CurrencyInfo("TND", "Tunisian Dinar", "Туніський динар", "🇹🇳", "туніський динар тунисский динар динар туніс динар тунис tunisian dinar tunisia"),
        CurrencyInfo("ARS", "Argentine Peso", "Аргентинське песо", "🇦🇷", "аргентинське песо аргентинское песо песо аргентина argentine peso argentina"),
        CurrencyInfo("CLP", "Chilean Peso", "Чилійське песо", "🇨🇱", "чилійське песо чилийское песо песо чилі песо чили chilean peso chile"),
        CurrencyInfo("COP", "Colombian Peso", "Колумбійське песо", "🇨🇴", "колумбійське песо колумбийское песо песо колумбія песо колумбия colombian peso colombia"),
        CurrencyInfo("PEN", "Peruvian Sol", "Перуанський соль", "🇵🇪", "перуанський соль перуанский соль соль перу sol peru"),
        CurrencyInfo("NPR", "Nepalese Rupee", "Непальська рупія", "🇳🇵", "непальська рупія непальская рупия рупія непал рупия непал nepalese rupee nepal"),
        CurrencyInfo("PKR", "Pakistani Rupee", "Пакистанська рупія", "🇵🇰", "пакистанська рупія пакистанская рупия рупія пакистан рупия пакистан pakistani rupee pakistan"),
        CurrencyInfo("BDT", "Bangladeshi Taka", "Бангладеська така", "🇧🇩", "така бангладеш бангладеська така бангладешская така taka bangladesh"),
        CurrencyInfo("LKR", "Sri Lankan Rupee", "Шрі-ланкійська рупія", "🇱🇰", "шрі ланкійська рупія шри ланкийская рупия рупія шрі ланка rupee sri lanka"),
        CurrencyInfo("ILS", "Israeli New Shekel", "Ізраїльський шекель", "🇮🇱", "шекель ізраїльський шекель израильский шекель shekel israel"),
        CurrencyInfo("SAR", "Saudi Riyal", "Саудівський ріал", "🇸🇦", "ріал риял саудівський ріал саудовский риял riyal saudi"),
        CurrencyInfo("QAR", "Qatari Riyal", "Катарський ріал", "🇶🇦", "катарський ріал катарский риял riyal qatar"),
        CurrencyInfo("KWD", "Kuwaiti Dinar", "Кувейтський динар", "🇰🇼", "кувейтський динар кувейтский динар dinar kuwait"),
        CurrencyInfo("INR", "Indian Rupee", "Індійська рупія", "🇮🇳", "рупія рупия індійська рупія индийская рупия rupee india"),
        CurrencyInfo("KRW", "South Korean Won", "Південнокорейська вона", "🇰🇷", "вона вон корейська вона корейская вона won korea"),
        CurrencyInfo("THB", "Thai Baht", "Тайський бат", "🇹🇭", "бат тайський бат тайский бат baht thailand"),
        CurrencyInfo("IDR", "Indonesian Rupiah", "Індонезійська рупія", "🇮🇩", "рупія рупия індонезійська рупія индонезийская рупия rupiah indonesia"),
        CurrencyInfo("MYR", "Malaysian Ringgit", "Малайзійський ринггіт", "🇲🇾", "ринггіт ринггит малайзійський ринггіт малайзийский ринггит ringgit malaysia"),
        CurrencyInfo("PHP", "Philippine Peso", "Філіппінське песо", "🇵🇭", "песо філіппінське песо филиппинское песо peso philippines"),
        CurrencyInfo("VND", "Vietnamese Dong", "Вʼєтнамський донг", "🇻🇳", "донг вʼєтнамський донг вьетнамский донг dong vietnam"),
        CurrencyInfo("BRL", "Brazilian Real", "Бразильський реал", "🇧🇷", "реал бразильський реал бразильский реал real brazil"),
        CurrencyInfo("MXN", "Mexican Peso", "Мексиканське песо", "🇲🇽", "песо мексиканське песо мексиканское песо peso mexico"),
        CurrencyInfo("ZAR", "South African Rand", "Південноафриканський ранд", "🇿🇦", "ранд південноафриканський ранд южноафриканский ранд rand south africa"),
        CurrencyInfo("TWD", "New Taiwan Dollar", "Новий тайванський долар", "🇹🇼", "тайванський долар тайваньский доллар новий тайванський долар новый тайваньский доллар taiwan dollar new taiwan dollar taiwan"),
        CurrencyInfo("MVR", "Maldivian Rufiyaa", "Мальдівська руфія", "🇲🇻", "мальдівська руфія мальдивская руфия руфія руфия rufiyaa maldives"),
        CurrencyInfo("OMR", "Omani Rial", "Оманський ріал", "🇴🇲", "оманський ріал оманский риал rial oman"),
        CurrencyInfo("JOD", "Jordanian Dinar", "Йорданський динар", "🇯🇴", "йорданський динар иорданский динар dinar jordan"),
        CurrencyInfo("KHR", "Cambodian Riel", "Камбоджійський рієль", "🇰🇭", "камбоджійський рієль камбоджийский риель рієль риель riel cambodia"),
        CurrencyInfo("LAK", "Lao Kip", "Лаоський кіп", "🇱🇦", "лаоський кіп лаосский кип кіп кип kip laos lao"),
        CurrencyInfo("TZS", "Tanzanian Shilling", "Танзанійський шилінг", "🇹🇿", "танзанійський шилінг танзанийский шиллинг shilling tanzania"),
        CurrencyInfo("KES", "Kenyan Shilling", "Кенійський шилінг", "🇰🇪", "кенійський шилінг кенийский шиллинг shilling kenya"),
        CurrencyInfo("SCR", "Seychellois Rupee", "Сейшельська рупія", "🇸🇨", "сейшельська рупія сейшельская рупия rupee seychelles"),
        CurrencyInfo("MUR", "Mauritian Rupee", "Маврикійська рупія", "🇲🇺", "маврикійська рупія маврикийская рупия rupee mauritius"),
        CurrencyInfo("NGN", "Nigerian Naira", "Нігерійська найра", "🇳🇬", "нігерійська найра нигерийская найра найра naira nigeria"),
        CurrencyInfo("GHS", "Ghanaian Cedi", "Ганський седі", "🇬🇭", "ганський седі ганский седи седі седи cedi ghana"),
        CurrencyInfo("XOF", "West African CFA Franc", "Західноафриканський франк CFA", "🌍", "західноафриканський франк cfa западноафриканский франк cfa cfa franc west african xof"),
        CurrencyInfo("XAF", "Central African CFA Franc", "Центральноафриканський франк CFA", "🌍", "центральноафриканський франк cfa центральноафриканский франк cfa cfa franc central african xaf"),
        CurrencyInfo("DOP", "Dominican Peso", "Домініканське песо", "🇩🇴", "домініканське песо доминиканское песо песо домінікана peso dominican republic"),
        CurrencyInfo("CRC", "Costa Rican Colon", "Коста-риканський колон", "🇨🇷", "коста-риканський колон коста ріканський колон костариканский колон colon costa rica"),
        CurrencyInfo("UYU", "Uruguayan Peso", "Уругвайське песо", "🇺🇾", "уругвайське песо уругвайское песо peso uruguay"),
        CurrencyInfo("JMD", "Jamaican Dollar", "Ямайський долар", "🇯🇲", "ямайський долар ямайский доллар dollar jamaica jamaican dollar"),
    )
    private val byCode = all.associateBy { it.code }
    fun find(code: String): CurrencyInfo? = byCode[code]
}