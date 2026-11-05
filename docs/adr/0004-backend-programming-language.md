# ADR 0004 · Lenguaje de programación backend
 
Estado: Aceptada · 20 de octubre de 2026
 
## Contexto
- El backend registra solicitudes de nuevos clientes de un banco y gestiona datos personales
  sensibles (DUI, ingresos, ubicación). Necesita autenticación, autorización, validación de
  entradas y transacciones de base de datos que dejen los datos consistentes.
- Somos dos personas en backend (se rotan por Sprint pero son dos máximo), con sprints cortos (4 días para trabajar), y no hay tiempo para implementar esas
  piezas a mano ni presupuesto para licencias. En la asignatura (y marco ágil) se valora más el software funcionando que implementaciones _exquisitas_.
- El equipo ya había trabajado con Java en asignaturas anteriores.
 
## Decisión
Usaremos Java 21 (LTS) con Spring Boot para el backend. 
Descartamos Python, que aunque vimos en otras asignaturas y el equipo cuenta con experiencia, nos iba requerir implementaciones manuales en muchos de los puntos mencionados en Contexto (especialmente la consistencia de los datos). 
Spring Boot y su autenticación, autorización, validación y transacciones vienen listas como
librerías maduras y no tenemos que construirlas ni mantenerlas.
 
## Consecuencias
+ Spring Security, Bean Validation y las transacciones de JPA ya existen y están probadas, así
  que dedicamos el tiempo a la lógica del onboarding y no a la infraestructura.
+ Nadie tuvo que aprender un lenguaje nuevo para empezar.
- Una App de Spring arranca más lento que Python (u otras opciones), y se verá reflejado si donde se despliega se apagan todas las instancias por inactividad.
- El entorno donde se despliegue debe ser compatible con la JVM.
- La versión de Java escogida (21 LTS), dejará de tener soporte principal en Septiembre 2028. Por lo que habrá que evaluar una actualización previa a esa fecha.
