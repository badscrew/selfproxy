# Third-Party Notices

This project uses third-party libraries and components. The following is a list of these dependencies and their respective licenses.

## Runtime Dependencies

### Kotlin and Kotlinx Libraries

#### Kotlin Standard Library
- **License**: Apache License 2.0
- **Copyright**: Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors
- **Website**: https://kotlinlang.org/

#### Kotlinx Coroutines
- **License**: Apache License 2.0
- **Copyright**: Copyright 2016-2023 JetBrains s.r.o.
- **Website**: https://github.com/Kotlin/kotlinx.coroutines

#### Kotlinx Serialization
- **License**: Apache License 2.0
- **Copyright**: Copyright 2017-2023 JetBrains s.r.o.
- **Website**: https://github.com/Kotlin/kotlinx.serialization

#### Kotlinx DateTime
- **License**: Apache License 2.0
- **Copyright**: Copyright 2019-2023 JetBrains s.r.o.
- **Website**: https://github.com/Kotlin/kotlinx-datetime

### Networking

#### Ktor Client
- **License**: Apache License 2.0
- **Copyright**: Copyright 2014-2023 JetBrains s.r.o.
- **Website**: https://ktor.io/

### Database

#### SQLDelight
- **License**: Apache License 2.0
- **Copyright**: Copyright 2016 Square, Inc.
- **Website**: https://cashapp.github.io/sqldelight/

### SSH Library

#### JSch (mwiede fork)
- **License**: BSD 3-Clause License
- **Copyright**: Copyright (c) 2002-2015 Atsuhiko Yamanaka, JCraft, Inc.
- **Website**: https://github.com/mwiede/jsch
- **Note**: This is a maintained fork with security fixes and modern algorithm support

**BSD 3-Clause License Text:**
```
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

  1. Redistributions of source code must retain the above copyright notice,
     this list of conditions and the following disclaimer.

  2. Redistributions in binary form must reproduce the above copyright 
     notice, this list of conditions and the following disclaimer in 
     the documentation and/or other materials provided with the distribution.

  3. The names of the authors may not be used to endorse or promote products
     derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
```

### Android Libraries

#### AndroidX Core
- **License**: Apache License 2.0
- **Copyright**: Copyright 2018 The Android Open Source Project
- **Website**: https://developer.android.com/jetpack/androidx

#### AndroidX Lifecycle
- **License**: Apache License 2.0
- **Copyright**: Copyright 2017 The Android Open Source Project
- **Website**: https://developer.android.com/jetpack/androidx/releases/lifecycle

#### AndroidX Security Crypto
- **License**: Apache License 2.0
- **Copyright**: Copyright 2019 The Android Open Source Project
- **Website**: https://developer.android.com/jetpack/androidx/releases/security

#### AndroidX DataStore
- **License**: Apache License 2.0
- **Copyright**: Copyright 2020 The Android Open Source Project
- **Website**: https://developer.android.com/topic/libraries/architecture/datastore

#### AndroidX WorkManager
- **License**: Apache License 2.0
- **Copyright**: Copyright 2018 The Android Open Source Project
- **Website**: https://developer.android.com/topic/libraries/architecture/workmanager

#### Jetpack Compose
- **License**: Apache License 2.0
- **Copyright**: Copyright 2019 The Android Open Source Project
- **Website**: https://developer.android.com/jetpack/compose

#### Navigation Compose
- **License**: Apache License 2.0
- **Copyright**: Copyright 2020 The Android Open Source Project
- **Website**: https://developer.android.com/jetpack/compose/navigation

### Dependency Injection

#### Hilt (Dagger)
- **License**: Apache License 2.0
- **Copyright**: Copyright 2012 The Dagger Authors
- **Website**: https://dagger.dev/hilt/

## Test Dependencies

### Testing Frameworks

#### JUnit
- **License**: Eclipse Public License 2.0
- **Copyright**: Copyright © 2002-2023 JUnit
- **Website**: https://junit.org/

#### Kotest
- **License**: Apache License 2.0
- **Copyright**: Copyright 2016-2023 Kotest contributors
- **Website**: https://kotest.io/

#### MockK
- **License**: Apache License 2.0
- **Copyright**: Copyright 2017-2023 Oleksiy Pylypenko and contributors
- **Website**: https://mockk.io/

#### Robolectric
- **License**: MIT License
- **Copyright**: Copyright (c) 2010-2023 Robolectric contributors
- **Website**: http://robolectric.org/

**MIT License Text:**
```
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
```

#### Espresso
- **License**: Apache License 2.0
- **Copyright**: Copyright 2015 The Android Open Source Project
- **Website**: https://developer.android.com/training/testing/espresso

## Build Tools

#### Gradle
- **License**: Apache License 2.0
- **Copyright**: Copyright 2008-2023 the original author or authors
- **Website**: https://gradle.org/

#### Android Gradle Plugin
- **License**: Apache License 2.0
- **Copyright**: Copyright (C) 2012 The Android Open Source Project
- **Website**: https://developer.android.com/studio/build

## License Texts

### Apache License 2.0

The full text of the Apache License 2.0 can be found at:
https://www.apache.org/licenses/LICENSE-2.0

Or in the LICENSE file in the root of this repository.

### Eclipse Public License 2.0

The full text of the Eclipse Public License 2.0 can be found at:
https://www.eclipse.org/legal/epl-2.0/

## Summary

This project uses the following licenses for its dependencies:

- **Apache License 2.0**: Majority of dependencies (Kotlin, AndroidX, Ktor, SQLDelight, Hilt, Kotest, MockK, Espresso)
- **BSD 3-Clause**: JSch SSH library
- **MIT License**: Robolectric (test dependency only)
- **Eclipse Public License 2.0**: JUnit (test dependency only)

All licenses are permissive and allow commercial use, modification, and distribution.

## Acknowledgments

We are grateful to all the open source projects and their contributors that make this project possible.

---

Last updated: November 30, 2025
