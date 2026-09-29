package com.deepblue.rescue.service;

import com.deepblue.rescue.domain.Animal;
import com.deepblue.rescue.domain.AnimalSex;
import com.deepblue.rescue.domain.RescueCase;
import com.deepblue.rescue.domain.RescueStatus;
import com.deepblue.rescue.domain.Specialist;
import com.deepblue.rescue.domain.Treatment;
import com.deepblue.rescue.domain.TreatmentType;
import com.deepblue.rescue.dto.request.CreateTreatmentRequest;
import com.deepblue.rescue.dto.response.TreatmentResponse;
import com.deepblue.rescue.exception.BusinessRuleException;
import com.deepblue.rescue.exception.ResourceNotFoundException;
import com.deepblue.rescue.mapper.TreatmentMapper;
import com.deepblue.rescue.repository.AnimalRepository;
import com.deepblue.rescue.repository.SpecialistRepository;
import com.deepblue.rescue.repository.TreatmentRepository;
import com.deepblue.rescue.service.impl.TreatmentServiceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreatmentServiceImplTest {

    private static final String ANIMAL_CODE = "AN-001";
    private static final String SPECIALIST_CODE = "SPEC-001";
    private static final LocalDate RESCUE_DATE = LocalDate.of(2026, 8, 20);
    private static final LocalDateTime VALID_DATE = LocalDateTime.of(2026, 8, 21, 9, 0);

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private SpecialistRepository specialistRepository;

    @Mock
    private TreatmentRepository treatmentRepository;

    @Mock
    private TreatmentMapper mapper;

    @InjectMocks
    private TreatmentServiceImpl service;

    @Test
    void shouldRegisterTreatmentWhenAllRulesAreMet() {

        Animal animal = animalWithCaseStatus(RescueStatus.IN_REHABILITATION);
        Specialist specialist = specialist(true);
        CreateTreatmentRequest request = request(VALID_DATE);
        TreatmentResponse response = new TreatmentResponse(
                1L, ANIMAL_CODE, SPECIALIST_CODE, VALID_DATE,
                TreatmentType.WOUND_CARE, "Cleaning of left front flipper injury.");

        when(animalRepository.findByAnimalCode(ANIMAL_CODE)).thenReturn(Optional.of(animal));
        when(specialistRepository.findByProfessionalCode(SPECIALIST_CODE))
                .thenReturn(Optional.of(specialist));
        when(treatmentRepository.save(any(Treatment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Treatment.class))).thenReturn(response);

        TreatmentResponse result = service.register(request);

        assertThat(result).isEqualTo(response);

        ArgumentCaptor<Treatment> captor = ArgumentCaptor.forClass(Treatment.class);
        verify(treatmentRepository).save(captor.capture());
        Treatment saved = captor.getValue();
        assertThat(saved.getAnimal()).isSameAs(animal);
        assertThat(saved.getSpecialist()).isSameAs(specialist);
        assertThat(saved.getPerformedAt()).isEqualTo(VALID_DATE);
        assertThat(saved.getType()).isEqualTo(TreatmentType.WOUND_CARE);
        assertThat(saved.getDescription())
                .isEqualTo("Cleaning of left front flipper injury.");
        verify(mapper).toResponse(saved);
    }

    @Test
    void shouldAllowTreatmentOnTheSameDayAsTheRescue() {

        Animal animal = animalWithCaseStatus(RescueStatus.UNDER_EVALUATION);
        LocalDateTime sameDay = RESCUE_DATE.atStartOfDay();

        when(animalRepository.findByAnimalCode(ANIMAL_CODE)).thenReturn(Optional.of(animal));
        when(specialistRepository.findByProfessionalCode(SPECIALIST_CODE))
                .thenReturn(Optional.of(specialist(true)));
        when(treatmentRepository.save(any(Treatment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Treatment.class)))
                .thenReturn(new TreatmentResponse(
                        1L, ANIMAL_CODE, SPECIALIST_CODE, sameDay,
                        TreatmentType.WOUND_CARE, null));

        TreatmentResponse result = service.register(request(sameDay));

        assertThat(result.performedAt()).isEqualTo(sameDay);
        verify(treatmentRepository).save(any(Treatment.class));
    }

    @Test
    void shouldThrowResourceNotFoundWhenAnimalDoesNotExist() {

        when(animalRepository.findByAnimalCode(ANIMAL_CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(request(VALID_DATE)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(ANIMAL_CODE);

        verifyNoInteractions(specialistRepository, treatmentRepository, mapper);
    }

    @Test
    void shouldThrowResourceNotFoundWhenSpecialistDoesNotExist() {

        when(animalRepository.findByAnimalCode(ANIMAL_CODE))
                .thenReturn(Optional.of(animalWithCaseStatus(RescueStatus.IN_REHABILITATION)));
        when(specialistRepository.findByProfessionalCode(SPECIALIST_CODE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(request(VALID_DATE)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(SPECIALIST_CODE);

        verify(treatmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectInactiveSpecialistAndNeverSave() {

        when(animalRepository.findByAnimalCode(ANIMAL_CODE))
                .thenReturn(Optional.of(animalWithCaseStatus(RescueStatus.IN_REHABILITATION)));
        when(specialistRepository.findByProfessionalCode(SPECIALIST_CODE))
                .thenReturn(Optional.of(specialist(false)));

        assertThatThrownBy(() -> service.register(request(VALID_DATE)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not active");

        verify(treatmentRepository, never()).save(any());
        verify(mapper, never()).toResponse(any());
    }

    @ParameterizedTest(name = "case {0} rejects new treatments")
    @EnumSource(value = RescueStatus.class, names = {"RELEASED", "CLOSED"})
    void shouldRejectTreatmentWhenCaseIsReleasedOrClosed(RescueStatus status) {

        when(animalRepository.findByAnimalCode(ANIMAL_CODE))
                .thenReturn(Optional.of(animalWithCaseStatus(status)));
        when(specialistRepository.findByProfessionalCode(SPECIALIST_CODE))
                .thenReturn(Optional.of(specialist(true)));

        CreateTreatmentRequest request = new CreateTreatmentRequest(
                ANIMAL_CODE, SPECIALIST_CODE, VALID_DATE,
                TreatmentType.OBSERVATION, "Routine observation.");

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(status.name());

        verify(treatmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectTreatmentWhenAnimalHasNoRescueCase() {

        Animal animalWithoutCase = new Animal(
                ANIMAL_CODE, "Green Sea Turtle", "Chelonia mydas", AnimalSex.UNKNOWN);

        when(animalRepository.findByAnimalCode(ANIMAL_CODE))
                .thenReturn(Optional.of(animalWithoutCase));
        when(specialistRepository.findByProfessionalCode(SPECIALIST_CODE))
                .thenReturn(Optional.of(specialist(true)));

        assertThatThrownBy(() -> service.register(request(VALID_DATE)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no rescue case");

        verify(treatmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectTreatmentDatedBeforeTheRescueDate() {

        when(animalRepository.findByAnimalCode(ANIMAL_CODE))
                .thenReturn(Optional.of(animalWithCaseStatus(RescueStatus.IN_REHABILITATION)));
        when(specialistRepository.findByProfessionalCode(SPECIALIST_CODE))
                .thenReturn(Optional.of(specialist(true)));

        CreateTreatmentRequest request = request(LocalDateTime.of(2026, 8, 15, 9, 0));

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("earlier than the rescue date");

        verify(treatmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectRequestWithoutDateOrType() {
        CreateTreatmentRequest withoutDate = new CreateTreatmentRequest(
                ANIMAL_CODE, SPECIALIST_CODE, null, TreatmentType.WOUND_CARE, "x");
        CreateTreatmentRequest withoutType = new CreateTreatmentRequest(
                ANIMAL_CODE, SPECIALIST_CODE, VALID_DATE, null, "x");

        assertThatThrownBy(() -> service.register(withoutDate))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.register(withoutType))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.register(null))
                .isInstanceOf(BusinessRuleException.class);

        verifyNoInteractions(animalRepository, specialistRepository, treatmentRepository, mapper);
    }

    @Test
    void shouldFindTreatmentsByAnimalCodeInChronologicalOrder() {

        Animal animal = animalWithCaseStatus(RescueStatus.IN_REHABILITATION);
        Specialist specialist = specialist(true);
        Treatment first = new Treatment(animal, specialist, VALID_DATE,
                TreatmentType.WOUND_CARE, "Wound cleaning");
        Treatment second = new Treatment(animal, specialist, VALID_DATE.plusDays(1),
                TreatmentType.HYDRATION, "Hydration");
        TreatmentResponse firstResponse = new TreatmentResponse(
                1L, ANIMAL_CODE, SPECIALIST_CODE, VALID_DATE,
                TreatmentType.WOUND_CARE, "Wound cleaning");
        TreatmentResponse secondResponse = new TreatmentResponse(
                2L, ANIMAL_CODE, SPECIALIST_CODE, VALID_DATE.plusDays(1),
                TreatmentType.HYDRATION, "Hydration");

        when(treatmentRepository.findByAnimalAnimalCodeOrderByPerformedAtAsc(ANIMAL_CODE))
                .thenReturn(List.of(first, second));
        when(mapper.toResponse(first)).thenReturn(firstResponse);
        when(mapper.toResponse(second)).thenReturn(secondResponse);

        List<TreatmentResponse> result = service.findByAnimalCode(ANIMAL_CODE);

        assertThat(result).containsExactly(firstResponse, secondResponse);
    }

    @Test
    void shouldReturnEmptyListWhenAnimalHasNoTreatments() {

        when(treatmentRepository.findByAnimalAnimalCodeOrderByPerformedAtAsc(ANIMAL_CODE))
                .thenReturn(List.of());

        List<TreatmentResponse> result = service.findByAnimalCode(ANIMAL_CODE);

        assertThat(result).isEmpty();
        verifyNoInteractions(mapper);
    }

    private Animal animalWithCaseStatus(RescueStatus status) {
        RescueCase rescueCase = new RescueCase(
                "RES-2026-100", RESCUE_DATE, "Santa Marta Bay", status);
        Animal animal = new Animal(
                ANIMAL_CODE, "Green Sea Turtle", "Chelonia mydas", AnimalSex.UNKNOWN);
        rescueCase.assignAnimal(animal);
        return animal;
    }

    private Specialist specialist(boolean active) {
        return new Specialist(
                SPECIALIST_CODE, "Elena", "Vargas", "elena.vargas@deepblue.org", active);
    }

    private CreateTreatmentRequest request(LocalDateTime performedAt) {
        return new CreateTreatmentRequest(
                ANIMAL_CODE, SPECIALIST_CODE, performedAt,
                TreatmentType.WOUND_CARE, "Cleaning of left front flipper injury.");
    }
}
