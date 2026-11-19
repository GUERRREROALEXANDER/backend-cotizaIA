package com.cotizaia;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** V30 applied on H2 (PostgreSQL mode), the same in-memory test DB as the rest of the suite. */
@SpringBootTest(properties = "llm.provider=stub")
@ActiveProfiles("test")
class V30EmailVerificationMigrationTests extends V30EmailVerificationMigrationContract {
}
