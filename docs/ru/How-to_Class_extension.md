---
title: 'How-to: Расширение классов'
---

Классическая схема для отделения связи между классами в отдельный модуль выглядит следующим образом:

Создаем модуль `MA`, в котором будет создаваться класс `A`:

```lsf
MODULE MA;

CLASS ABSTRACT A; // объявляем абстрактный класс
a = ABSTRACT BPSTRING[10] (A); // объявляем абстрактное свойство a
```

Создаем модуль `MB`, в котором будет создаваться класс `B`:

```lsf
MODULE MB;

CLASS B; // объявляем класс B
b = DATA BPSTRING[10] (B); // объявляем первичное свойство b для класса B
```

Создаем модуль `MBA`, в котором будет определяться связь между классами `A` и `B`:

```lsf
MODULE MBA;

// указываем, что модуль MBA зависит от модулей MA и MB, чтобы в нем можно было использовать элементы системы,
// объявляемые в них
REQUIRE MA, MB; 

EXTEND CLASS B : A; // донаследуем класс B от A
// указываем, что для абстрактного свойства a, в качестве реализации должно использоваться свойство B
a(ba) += b(ba); 
```

Таким образом, непосредственной зависимости между модулями `MA` и `MB`, что позволяет включать/отключать связь между ними при необходимости путем подключения модуля `MBA`. Следует отметить, что модуль `MBA` расширяет функциональность модуля `MB`, не изменяя при этом его кода.

Применять mixin классов при использовании метакода можно следующим образом:

Предположим, что у нас существует метакод, который объявляет класс и задает ему определенные свойства:

```lsf
MODULE MyModule;

META defineMyClass (className) // объявляем метакод defineMyClass с параметром className
    CLASS className; // объявляем класс с именем className
    // добавляем для созданного класса свойство с именем myProperty+className
    myProperty###className = DATA BPSTRING[20] (className); 
END
```

Следует отметить, что при вызове этого метакода, невозможно указать классы, от которого должно происходить наследование создаваемого класса. Однако, это можно реализовать посредством mixin'а классов следующим образом:

```lsf
CLASS MySuperClass;

@defineMyClass(MyClass); // вызываем метакод, который создаст класс и свойство

// наследуем MyClass от MySuperClass, при этом MyClass "получит" все свойства,
// которые объявлены для класса MySuperClass
EXTEND CLASS MyClass : MySuperClass; 
```