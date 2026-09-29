package com.deepblue.rescue.service;

import com.deepblue.rescue.domain.Animal;
import com.deepblue.rescue.domain.AnimalSex;
import com.deepblue.rescue.domain.RescueCase;
import com.deepblue.rescue.domain.RescueCenter;
import com.deepblue.rescue.domain.RescueStatus;
import com.deepblue.rescue.dto.request.ChangeRescueStatusRequest;
import com.deepblue.rescue.dto.response.RescueCaseResponse;
import com.deepblue.rescue.exception.BusinessRuleException;
import com.deepblue.rescue.exception.ResourceNotFoundException;
import com.deepblue.rescue.mapper.RescueCaseMapper;
import com.deepblue.rescue.repository.RescueCaseRepository;
import com.deepblue.rescue.service.impl.RescueCaseServiceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
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
class RescueCaseServiceImplTest {

    private static final String CASE_CODE = "RES-001";

    @Mock
    private RescueCaseRepository repository;

    @Mock
    private RescueCaseMapper mapper;

    @InjectMocks
    private RescueCaseServiceImpl service;

    @Test
    void shouldFindRescueCaseByCode() {

        RescueCase rescueCase = rescueCaseWithStatus(RescueStatus.ADMITTED);
        RescueCaseResponse response = responseFor(RescueStatus.ADMITTED);

        when(repository.findByCaseCode(CASE_CODE)).thenReturn(Optional.of(rescueCase));
        when(mapper.toResponse(rescueCase)).thenReturn(response);

        RescueCaseResponse result = service.findByCode(CASE_CODE);

        assertThat(result).isEqualTo(response);
        verify(repository).findByCaseCode(CASE_CODE);
        verify(mapper).toResponse(rescueCase);
    }

    @Test
    void shouldThrowResourceNotFoundWhenRescueCaseDoesNotExist() {

        when(repository.findByCaseCode("RES-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByCode("RES-999"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("RES-999");

        verify(mapper, never()).toResponse(any());
    }

    @Test
    void shouldFindRescueCasesByStatus() {

        RescueCase first = rescueCaseWithStatus(RescueStatus.IN_REHABILITATION);
        RescueCase second = rescueCaseWithStatus(RescueStatus.IN_REHABILITATION);
        RescueCaseResponse firstResponse = responseFor(RescueStatus.IN_REHABILITATION);
        RescueCaseResponse secondResponse = responseFor(RescueStatus.IN_REHABILITATION);

        when(repository.findByStatusOrderByRescueDateAsc(RescueStatus.IN_REHABILITATION))
                .thenReturn(List.of(first, second));
        when(mapper.toResponse(first)).thenReturn(firstResponse);
        when(mapper.toResponse(second)).thenReturn(secondResponse);

        List<RescueCaseResponse> result =
                service.findByStatus(RescueStatus.IN_REHABILITATION);

        assertThat(result).containsExactly(firstResponse, secondResponse);
    }

    @Test
    void shouldReturnEmptyListWhenNoCaseHasThatStatus() {

        when(repository.findByStatusOrderByRescueDateAsc(RescueStatus.CLOSED))
                .thenReturn(List.of());

        List<RescueCaseResponse> result = service.findByStatus(RescueStatus.CLOSED);

        assertThat(result).isEmpty();
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldChangeStatusWhenTransitionIsValid() {

        RescueCase rescueCase = rescueCaseWithStatus(RescueStatus.ADMITTED);
        RescueCaseResponse response = responseFor(RescueStatus.UNDER_EVALUATION);

        when(repository.findByCaseCode(CASE_CODE)).thenReturn(Optional.of(rescueCase));
        when(repository.save(rescueCase)).thenReturn(rescueCase);
        when(mapper.toResponse(rescueCase)).thenReturn(response);

        RescueCaseResponse result = service.changeStatus(
                CASE_CODE,
                new ChangeRescueStatusRequest(RescueStatus.UNDER_EVALUATION));

        assertThat(result).isEqualTo(response);
        assertThat(rescueCase.getStatus()).isEqualTo(RescueStatus.UNDER_EVALUATION);
        verify(repository).save(rescueCase);
    }

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
            "ADMITTED,          UNDER_EVALUATION",
            "UNDER_EVALUATION,  IN_REHABILITATION",
            "IN_REHABILITATION, READY_FOR_RELEASE",
            "READY_FOR_RELEASE, RELEASED"
    })
    void shouldAllowEveryStepOfTheRescueFlow(RescueStatus current, RescueStatus next) {

        RescueCase rescueCase = rescueCaseWithStatus(current);
        RescueCaseResponse response = responseFor(next);

        when(repository.findByCaseCode(CASE_CODE)).thenReturn(Optional.of(rescueCase));
        when(repository.save(rescueCase)).thenReturn(rescueCase);
        when(mapper.toResponse(rescueCase)).thenReturn(response);

        RescueCaseResponse result =
                service.changeStatus(CASE_CODE, new ChangeRescueStatusRequest(next));

        assertThat(result.status()).isEqualTo(next);
        assertThat(rescueCase.getStatus()).isEqualTo(next);
        verify(repository).save(rescueCase);
    }

    @Test
    void shouldRejectInvalidTransitionAndNeverSave() {

        RescueCase rescueCase = rescueCaseWithStatus(RescueStatus.ADMITTED);
        when(repository.findByCaseCode(CASE_CODE)).thenReturn(Optional.of(rescueCase));

        assertThatThrownBy(() -> service.changeStatus(
                CASE_CODE,
                new ChangeRescueStatusRequest(RescueStatus.READY_FOR_RELEASE)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ADMITTED")
                .hasMessageContaining("READY_FOR_RELEASE");

        assertThat(rescueCase.getStatus()).isEqualTo(RescueStatus.ADMITTED);
        verify(repository, never()).save(any());
        verify(mapper, never()).toResponse(any());
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @CsvSource({
            "ADMITTED,          RELEASED",
            "ADMITTED,          ADMITTED",
            "UNDER_EVALUATION,  ADMITTED",
            "IN_REHABILITATION, UNDER_EVALUATION",
            "READY_FOR_RELEASE, IN_REHABILITATION",
            "RELEASED,          IN_REHABILITATION",
            "RELEASED,          ADMITTED",
            "CLOSED,            ADMITTED"
    })
    void shouldRejectEveryTransitionOutsideTheFlow(RescueStatus current, RescueStatus next) {

        RescueCase rescueCase = rescueCaseWithStatus(current);
        when(repository.findByCaseCode(CASE_CODE)).thenReturn(Optional.of(rescueCase));

        assertThatThrownBy(() -> service.changeStatus(
                CASE_CODE, new ChangeRescueStatusRequest(next)))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(rescueCase.getStatus()).isEqualTo(current);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldThrowResourceNotFoundWhenChangingStatusOfUnknownCase() {

        when(repository.findByCaseCode("RES-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus(
                "RES-999",
                new ChangeRescueStatusRequest(RescueStatus.UNDER_EVALUATION)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("RES-999");

        verify(repository, never()).save(any());
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldRejectRequestWithoutTargetStatus() {

        assertThatThrownBy(() -> service.changeStatus(
                CASE_CODE, new ChangeRescueStatusRequest(null)))
                .isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(() -> service.changeStatus(CASE_CODE, null))
                .isInstanceOf(BusinessRuleException.class);

        verifyNoInteractions(repository, mapper);
    }

    private RescueCase rescueCaseWithStatus(RescueStatus status) {
        RescueCase rescueCase = new RescueCase(
                CASE_CODE, LocalDate.of(2026, 8, 20), "Santa Marta Bay", status);

        new RescueCenter("RC-001", "Santa Marta Center", "Santa Marta")
                .addCase(rescueCase);
        rescueCase.assignAnimal(new Animal(
                "AN-001", "Green Sea Turtle", "Chelonia mydas", AnimalSex.UNKNOWN));

        return rescueCase;
    }

    private RescueCaseResponse responseFor(RescueStatus status) {
        return new RescueCaseResponse(
                1L, CASE_CODE, LocalDate.of(2026, 8, 20),
                "Santa Marta Bay", status, "RC-001", "AN-001");
    }
}
