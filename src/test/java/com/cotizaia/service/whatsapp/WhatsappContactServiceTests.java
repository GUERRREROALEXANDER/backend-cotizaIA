package com.cotizaia.service.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotizaia.crypto.BlindIndex;
import com.cotizaia.domain.Agency;
import com.cotizaia.domain.AppUser;
import com.cotizaia.domain.UserRole;
import com.cotizaia.domain.WhatsappMessage;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.AppUserRepository;
import com.cotizaia.repository.WhatsappContactRepository;
import com.cotizaia.repository.WhatsappMessageRepository;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Verifying stores the number's blind index, so an incoming message finds its user, and a number has one owner. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WhatsappContactServiceTests {

    @Autowired
    private WhatsappContactRepository contacts;

    @Autowired
    private WhatsappMessageRepository messages;

    @Autowired
    private AgencyRepository agencies;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private BlindIndex blindIndex;

    private final PhoneVerificationsTests.MovableClock clock = new PhoneVerificationsTests.MovableClock();

    private WhatsappContactService service() {
        return new WhatsappContactService(contacts, new WhatsappOutbox(messages, clock), new PhoneVerifications(clock),
                blindIndex, clock);
    }

    @Test
    void aVerifiedNumberIsFoundHoweverWhatsappWritesIt() {
        WhatsappContactService service = service();
        Long userId = user();

        verify(service, userId, "+57 301 094 4197");

        assertThat(service.findByPhone("+573010944197")).hasValueSatisfying(contact ->
                assertThat(contact.getUserId()).isEqualTo(userId));
        assertThat(service.findByPhone("+57-301-094-4197")).isPresent();
        assertThat(service.findByPhone("+573009999999")).isEmpty();
        assertThat(service.findByPhone("not a phone")).isEmpty();
    }

    @Test
    void verifyingANumberOnAnotherAccountMovesIt() {
        WhatsappContactService service = service();
        Long first = user();
        Long second = user();
        verify(service, first, "+573010944197");
        clock.advance(PhoneVerifications.COOLDOWN);

        verify(service, second, "+573010944197");

        assertThat(contacts.findById(first)).isEmpty();
        assertThat(service.findByPhone("+573010944197")).hasValueSatisfying(contact ->
                assertThat(contact.getUserId()).isEqualTo(second));
    }

    @Test
    void changingYourOwnNumberUpdatesTheLookup() {
        WhatsappContactService service = service();
        Long userId = user();
        verify(service, userId, "+573010944197");
        clock.advance(PhoneVerifications.COOLDOWN);

        verify(service, userId, "+573015550000");

        assertThat(service.findByPhone("+573010944197")).isEmpty();
        assertThat(service.findByPhone("+573015550000")).isPresent();
    }

    private void verify(WhatsappContactService service, Long userId, String phone) {
        service.startVerification(userId, phone);
        String normalized = PhoneNumbers.normalize(phone);
        String body = messages.findAll().stream().filter(message -> message.getPhone().equals(normalized))
                .map(WhatsappMessage::getBody).reduce((older, newer) -> newer).orElseThrow();
        Matcher code = Pattern.compile("(\\d{6})").matcher(body);
        assertThat(code.find()).isTrue();
        assertThat(service.verify(userId, code.group(1))).isPresent();
    }

    private Long user() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Agency agency = agencies.saveAndFlush(new Agency("Agencia " + suffix));
        return users.saveAndFlush(new AppUser(agency, "Owner", "owner-" + suffix + "@demo.co", UserRole.OWNER))
                .getId();
    }
}
