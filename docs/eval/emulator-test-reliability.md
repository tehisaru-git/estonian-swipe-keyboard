# Emulaatorikatse usaldusväärsus Estonian Swipe'i arenduses

## Miks aeglane virtualiseerimine võib jätta vale mulje

Androidi jõudlusjuhend hoiatab, et emulaatori mõõdud sõltuvad hosti protsessorist ja koormusest ega esinda päris telefoni; Macrobenchmark käsitleb emulaatorit otsese veahoiatusena, mida ei tohiks lihtsalt maha suruda.[1][2] Graafika- ja VM-kiirendus on eraldi, mõlemad mõjutavad emulaatori tööd; `/dev/kvm` olemasolu üksi ei tõesta, et meie konkreetne emulaator oleks kiirendatud.[3]

**Selle masina vaatluspilt 2026-11-11 09:42 EET:** hosti vahetusmälu oli kasutusel 8,6/9,0 GiB; külalissüsteemil oli 2,5 GiB RAM-i ja umbes 1,08 GiB `MemAvailable`. Emulaatori käsk küsib 2 CPU-tuuma, 2560 MiB mälu, `-accel on` ja `-gpu host`; need on käivitusparameetrid, mitte kiirenduse kvaliteedi mõõt. Samal hetkel kasutas Gradle'i Java-protsess ligikaudu 201% CPU-d. Seega oleks praegune host eriti halb koht viipe viiteaja või kaadrite sujuvuse absoluutseks hindamiseks.

Võimalikud veapildid:
- **Vale negatiivne:** mudeli/sõnastiku külmkäivitus, aeglane dekodeerimine või ülekoormatud põhilõim jätab soovituse hiljaks; järgmine klahvivajutus tühistab poolelioleva viipe. Seda ei tohi automaatselt nimetada „eesti sõna ei tunta ära”, aga kui see juhtub päris seadmes, on see ikkagi tooteviga.
- **Katseriista viga:** meie `build/tooling/ime-smoke/probe.py` saadab iga puutepunkti eraldi ADB-käsuna, kasutab fikseeritud 12 sekundi eelsoojendus- ja muid ooteaegu ning loeb UIAutomatori puu eraldi. Ühes katses teatas puu „No test editor found” hoolimata ekraanil nähtavast väljast, teises oli tegelikult avatud seadete vaade. Veel olulisem: `am start -S` peatas samal ajal valitud katsetatava IME ning Android valis teise klaviatuuri; järgnenud ingliskeelne „Tree” tulemus polnud üldse uue klaviatuuri ennustus. Katse peab pärast rakenduse taaskäivitust kontrollima ja vajaduse korral taastama täpse `default_input_method` ID ning kontrollima esiplaani vaadet enne punktide saatmist. Need on katseriista olekuvead, mitte tõendid dekoodri rikkest.
- **Vale positiivne:** emulaatoris õnnestunud etteantud sirgjooneline sünteetiline viibe ega kiire dekodeerimine ei kinnita sõrmega tehtud eesti viipe täpsust ega Pixel 8 kiirust. x86_64 külaline, mis reklaamib ka arm64 ABI-t, ei ole Pixel 8 Tensor-protsessor ega selle puuteekraan.
- **Tegelik funktsiooniviga:** sama salvestatud jälg annab järjekindlalt vale kandidaadi, kaotab tekstivälja sisu või kirjutab teise välja. Seda ei tohi vabandada emulaatori aeglusega. Otsustusreegel peab arvestama sündmuste arvu, aktiivset välja/keelt, dekoodri tulemust ja redaktori lõplikku teksti.

## Kontrolliprotseduur

1. **Eralda kolm mõõtu.** (a) puhtad sõnastiku/dekoodri testid etteantud jäljega; (b) emulaatoris sisestuse korrektsus ja elutsükkel; (c) jõudlus ning sõrmega tehtud viibete kvaliteet päris Pixel 8-l. Emulaator annab (b), mitte (c).[1][2]
2. **Mõõda seadme sees monotonne ajajoon.** `MotionEvent.eventTime`/`SystemClock.elapsedRealtimeNanos`: sõrm alla, üles, dekodeerimine algas, tulemus valmis, kirjutus kinnitati, soovitusriba joonistati. Salvesta külm/soe käivitus eraldi, jälje punktide arv, keel, geomeetria ja loobumise põhjus. ADB käsu kestus ei ole viipe ega dekoodri viitaeg.
3. **Katse eeltingimused:** ei ehita Gradle'iga samal ajal; kontrolli hosti RAM/vahetusmälu/CPU koormust ja külalise `MemAvailable`-it; üksainus emulaator, ära peata teiste rakenduste jälgimist. Kasuta üht ja sama APK-d, Androidi versiooni, resolutsiooni ja külma/sooja olekut. Kui taustkoormus on suur, jäta jõudlusmõõt kehtetuks, mitte ära vali sobivat jooksu.
4. **Asenda pime ooteaeg tingimusega.** Pärast IME valimist ja välja avamist oota piiritletud aja jooksul tegelikku editori/IME olekut; säilita vastuolu korral ekraanipilt, XML-puu, logcat'i oluline osa ja ajajoon. Automaatne kordus on lubatud katseriista vea kinnitamiseks, kuid algne ebaõnnestumine jääb raportisse. Külma esimesel viipel ei tohi eelsoojendust salaja lõpule oodata.
5. **Korda ning võrrelge.** Sama salvestatud jälg dekoodrisse deterministlikult korduvalt; korratav vale top-1/top-3 näitab andmestiku, geomeetria või järjestuse probleemi. Kui muutub ainult ADB/UIAutomatori katse tulemus, uurida sündmuste kadu ja hosti koormust. Lõplik p50/p95 ning tõeline parandamise sujuvus mõõta füüsilisel Pixel 8-l eri katseplokkides; Androidi juhend soovitab jõudlustestideks päris seadet.[1][4]

**Piir:** seni pole päris Pixel 8 viipe- ega viiteaeg mõõdetud; emulaatori praegust protsessiparameetrit ei tõlgendata KVM-i toimivuse kinnituseks.

## Sources

[1] https://developer.android.com/topic/performance/benchmarking/benchmarking-in-ci
[2] https://developer.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args
[3] https://developer.android.com/studio/run/emulator-acceleration
[4] https://developer.android.com/codelabs/android-macrobenchmark-inspect
