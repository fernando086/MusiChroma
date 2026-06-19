# MusiChroma 🎵

### *Monitoreo emocional inteligente y soporte en sesiones de musicoterapia mediante Deep Learning*

---

## 📝 Descripción del Proyecto
**MusiChroma** es una aplicación móvil avanzada diseñada para enriquecer y estructurar las sesiones de musicoterapia y educación emocional. Utilizando un modelo de aprendizaje profundo basado en una arquitectura **CNN1D-BiGRU-Attention**, el sistema realiza análisis continuos del contenido de audio para estimar el impacto emocional de las pistas en tiempo real. 

Esta solución tecnológica sirve como un soporte objetivo para el musicoterapeuta, traduciendo variables acústicas complejas en interpretaciones emocionales claras basadas en modelos validados de psicología.

---

## ⚙️ Arquitectura del Sistema
El ecosistema de **MusiChroma** está compuesto por las siguientes tecnologías integradas:

* **📱 Cliente Android:** Desarrollado en Java y XML nativo con Material Design, garantizando una interfaz premium, fluida e intuitiva para el terapeuta.
* **🐍 Servidor Flask:** Backend de alto rendimiento que expone las APIs REST necesarias para el procesamiento acústico y la orquestación del modelo.
* **🗄️ Base de Datos:** PostgreSQL para un almacenamiento persistente robusto, seguro y estructurado de sesiones, pacientes e históricos de canciones.
* **🔒 Autenticación:** Integración con Google Firebase Auth para el inicio de sesión seguro de los terapeutas.

---

## 🎶 Enfoque BYOM (Bring Your Own Music)
Para garantizar la **máxima confiabilidad, independencia y cumplimiento normativo**, MusiChroma adopta el enfoque **BYOM (Bring Your Own Music)**:
* **Procesamiento Local Estricto:** Se procesan únicamente archivos de audio almacenados localmente en el dispositivo del usuario o del terapeuta.
* **Formatos Compatibles:** Soporte completo para pistas de audio en formatos `.mp3`, `.wav` y `.ogg`.
* **Capacidad Máxima:** Límite flexible de hasta **100 MB** por pista de audio, óptimo para la carga rápida y el procesamiento en el servidor sin latencia.
* **Sin Dependencias de Terceros:** Al prescindir de servicios externos de descarga o streaming, el sistema asegura un funcionamiento autónomo y continuo, inmune a cambios de políticas de APIs externas o caídas de red de plataformas terceras.

---

## 🌟 Características Principales

| Característica | Detalle Técnico / Funcional |
| :--- | :--- |
| 🧠 **Inferencia Emocional Continua** | Estimación en tiempo real de dimensiones afectivas (**Valence** y **Arousal**) mediante el modelo de aprendizaje profundo. |
| 📊 **Mapeo al Modelo de Plutchik** | Clasificación en **8 emociones básicas** y **3 niveles de intensidad** representados en un gráfico interactivo. |
| 🔊 **Reproducción Concurrente Estable** | Reproductor de audio local integrado diseñado con sincronización precisa del gráfico emocional durante la reproducción. |
| 📄 **Informes Clínicos Automatizados** | Exportación automatizada de históricos y notas de sesión a formatos descargables **PDF** y **Word** (`.docx`). |

---

## 🚀 Metodología de Desarrollo

Este proyecto fue desarrollado bajo una metodología **AI-Augmented Software Engineering** (Ingeniería de Software Aumentada por IA), utilizando herramientas avanzadas de asistencia al desarrollo, entre ellas **Gemini** (a través de Antigravity IDE), **ChatGPT** y **DeepSeek**. Esto permitió la rápida implementación de la arquitectura de Deep Learning en el backend, la estructuración robusta de la base de datos y la creación de interfaces de usuario pulidas de acuerdo a los estándares de calidad del software.
