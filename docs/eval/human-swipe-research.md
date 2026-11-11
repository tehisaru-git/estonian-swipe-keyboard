# Inimese viipejälg: uuringud ja Estonian Swipe'i katseplaan

## Mida uuringutest tegelikult järeldada saab

- Leiva jt „How We Swipe” kogus veebipõhisel QWERTY-klaviatuuril 1338 osaleja 11 318 erineva **ingliskeelse** sõna viiped ja üle kaheksa miljoni puutepunkti. Sama sõna trajektoorid erinevad inimestel; salvestati koordinaadid, ajatemplid, puuteala ja klaviatuuri mõõtmed.[1][7]
- Inimene alustab esimese tähe lähedal, liigub katkematult järgmiste tähtede piirkondade kaudu ja lõpetab viimase tähe lähedal; trajektoor ei pea tabama iga klahvi keskpunkti. Uuringu *andmekogumise* tolerants oli teadlikult suur (raadius 1,5 × klahvi suurem mõõde), et mitte kasutajat asjata takistada. Seda arvu **ei tohi** otse üle võtta meie dekoodri otsustusläveks.[1]
- Uuringu brauseris olid puutesündmused ligikaudu 16 ms sammuga; samal kasutajal tuleb arvestada erineva kiiruse, ebaühtlaste proovide ja ekraanimõõduga. Nende veebipõhine test ei kasutanud sisestamise ajal statistilist dekoodrit ega sõnasoovitusi; hilisem sõnavea hinnang tehti tagantjärele. Seega pole uuringu vea- ega kiirusnäitajad meie Androidi klaviatuuri kvaliteedinorm.[1]
- Quinn ja Zhai kirjeldavad inimese liikumist sujuva, väikese tõukega (minimum-jerk) trajektoorina, millel on mõni mürarikas vahepunkt, mitte tähe keskpunktide sirglõikude jadana. See on hea alus **katsejälgede varieerimiseks**, mitte tõend, et üks sünteetiline mudel asendaks päris kasutajate viiped.[2]
- Uuring leidis seoseid kiiruse ja tuttavuse, keeleoskuse, pöidlaga viipamise ning ekraani suuruse vahel; sagedased ja haruldased sõnad käitusid erinevalt. Need on ingliskeelse veebikatse tähelepanekud, mitte eesti keele ega Pixel 8 jaoks mõõdetud efekti suurused.[1]

## Kuidas seda arenduses kasutada

1. **Koosta nõusolekupõhine Pixel 8 katse.** Näita ainult etteantud mitteisiklikke eesti ja inglise sõnu/lauseid; salvesta kohaliku katse jaoks sõna, valitud keele, klaviatuuri mõõdetud geomeetria, sõrme `x/y/t` jälje, lõpptulemuse, kolme pakkumise ja kasutaja paranduse. Ära kogu paroole, vabateksti ega tavapärast sisestusajalugu. Jäljed jäävad seadmesse, kuni testija otsustab need eraldi välja anda. Uuringu kogumisrakendus on avalik, kuid selle lähtekoodi või andmeid ei kopeerita meie rakendusse ilma eraldi litsentsikontrollita.[1][5]
2. **Tee päris jälgedest arendus- ja pimekatse.** Jaota osaleja ning sõna kaupa, et sama inimese või sõna variatsioonid ei lekiks mõlemasse ossa. Vaata eraldi sagedasi sõnu, käändeid/pöördeid, liitsõnu, õ/ä/ö/ü, `š/ž`, lühikesi mitmetimõistetavaid sõnu, haruldasi ja sõnastikust puuduvaid sõnu; inglise valim olgu eraldi. Võrdle ka pöidla/nimetissõrme, ühe käe ja klahvistiku servade juhtumeid. Inglise „How We Swipe” jäljed sobivad formaadi ja üldise häiringukindluse prooviks, **mitte eesti täpsuse tõestuseks**.[1][7]
3. **Laienda automaatkatseid kalibreeritud häiringutega.** Lisaks senistele sirgjoonelistele ADB keskpunktiviibetele testi nihkunud algust/lõppu, ümaraid või nurki lõikavaid pöördeid, erinevat kiirust ning ajaliselt hõredaid ja ebaühtlasi punkte, kordustähti ja väljaspoole klahviala lõppevat jälge. Loo variatsioonid päris katsejälgede ja mõõdetud geomeetria jaotustest; ära häälesta juhuslikku mürataset „tunde järgi”. Quinn–Zhai mudel annab põhjuse sujuvust ja vahepunkte eelistada sirglõikudele.[1][2]
4. **Mõõda inimese töö, mitte üksiku ADB sõna õnnestumine.** Iga testirühma jaoks: top-1, top-3, sõnaviga pärast parandusi, paranduste arv, puudunud viiped, viipest esimese soovituseni p50/p95 ning seansi hangumised. Päris Pixel 8-l võrdle sama etteantud teksti ja sarnase harjutusajaga Gboardi vastu; raporteeri Eesti/English eraldi ja hoia pimekatse väljaspool häälestust. Uuring eristas samuti sõnataseme trajektoori ja lausetaseme sooritust ning näitas, et sagedusmudel mõjutab dekodeerimist.[1]

**Piirang:** seni on meie klaviatuuris kontrollitud vaid üksikud sünteetilised emulaatoriviiped; ei ole kogutud ega mõõdetud Pixel 8 päris eesti viipejälgi. Avaliku ingliskeelse andmestiku kasutusõigused ja vorming tuleb enne importi eraldi üle kontrollida.[1][8]

## Sources

[1] https://aaltodoc.aalto.fi/server/api/core/bitstreams/ce09cb57-cdec-4c1a-986c-4b8e49cda376/content
[2] https://research.google/pubs/pub45730
[5] https://github.com/luileito/swipetest
[7] https://aaltodoc.aalto.fi/items/58ce6a66-347c-4f0d-a19f-021920cda043
[8] https://osf.io/sj67f
