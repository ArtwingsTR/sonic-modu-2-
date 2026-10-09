# Sonic Generations (Minecraft 1.21.1, Fabric)

## Kontroller
- **R (basili tut)**: Boost. Basinca anlik ileri firlama + hizlanma, kollar arkada Sonic kosusu, mavi enerji, ruzgar sesi.
- **Havada Space / G**: Homing Attack. Sonic top haline gelir, hedefe doner, carpinca sekerek ses + efekt.
- Modeli gormek icin **F5** (ucuncu sahis kamera). Birinci sahiste Steve kolu gizlenir.

## Ince ayar (SonicClient.java, en ustteki sabitler)
- BOOST_MAX_SPEED (1.8 = 36 blok/sn), BOOST_START_KICK, BOOST_ACCEL
- GAUGE_DRAIN / GAUGE_REGEN (boost ne kadar surer / dolar)
- HOMING_SPEED, FOV_MAX_BONUS
- Model boyu: SonicRenderer.SCALE (1.45)

## Dosyalar
- `assets/sonicmod/model/sonic.bin` : iskeletli Sonic (Unleashed/Generations modelinden donusturuldu)
- `assets/sonicmod/textures/entity/` : model dokulari + aura/glow efektleri
- `assets/sonicmod/sounds/` : kod ile sentezlenmis sesler (boost_start, boost_loop, homing_launch, homing_hit)
- Ses degistirmek icin ayni isimli .ogg dosyasini degistirmen yeterli.

## Not
Model orijinal yapimcilarina / Sega'ya aittir; kisisel kullanim icindir.
