package com.simucredito.client.application.service;

import com.simucredito.client.application.dto.*;
import com.simucredito.client.domain.model.Client;
import com.simucredito.client.domain.model.Person;
import com.simucredito.client.domain.repository.ClientRepository;
import com.simucredito.client.domain.repository.PersonRepository;
import com.simucredito.configuration.application.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final PersonRepository personRepository;
    private final ModelMapper modelMapper;
    private final ConfigurationService configurationService;

    @Transactional
    public ClientDTO createClient(CreateClientRequestDTO request) {
        Long userId = getCurrentUserId();

        // Validate that document number is not already in use
        if (personRepository.existsByDocumentNumber(request.getHolder().getDocumentNumber())) {
            throw new RuntimeException("Document number already exists: " + request.getHolder().getDocumentNumber());
        }

        // Validate birth date - must be 18+ years old
        validateBirthDate(request.getHolder().getBirthDate());

        // Create holder person
        Person holder = createPerson(request.getHolder());
        personRepository.save(holder);

        // Create spouse person only if marital status requires it
        Person spouse = null;
        if (shouldHaveSpouse(holder.getMaritalStatusId())) {
            if (request.getSpouse() != null) {
                // Validate that spouse document number is not already in use
                if (personRepository.existsByDocumentNumber(request.getSpouse().getDocumentNumber())) {
                    throw new RuntimeException("Spouse document number already exists: " + request.getSpouse().getDocumentNumber());
                }
                // Validate spouse birth date - must be 18+ years old
                validateBirthDate(request.getSpouse().getBirthDate());
                spouse = createPerson(request.getSpouse());
                personRepository.save(spouse);
            } else {
                throw new RuntimeException("Spouse information is required for married or cohabiting clients");
            }
        }

        // Create client
        Client client = Client.builder()
                .userId(userId)
                .holderId(holder.getId())
                .spouseId(spouse != null ? spouse.getId() : null)
                .fundSourceId(request.getFundSourceId())
                .familyNetIncome(request.getFamilyNetIncome())
                .appliesForIntegratorBonus(request.getAppliesForIntegratorBonus())
                .conadisCardNumber(request.getConadisCardNumber())
                .isOwnerOfAnotherProperty(request.getIsOwnerOfAnotherProperty())
                .receivedPreviousSupport(request.getReceivedPreviousSupport())
                .build();

        client = clientRepository.save(client);

        // Perform initial pre-qualification
        performInitialPreQualification(client, holder, spouse);

        return buildClientDTO(client, holder, spouse);
    }

    public List<ClientDTO> getClientsByUser() {
        Long userId = getCurrentUserId();
        List<Client> clients = clientRepository.findByUserId(userId);

        return clients.stream()
                .map(this::buildClientDTO)
                .collect(Collectors.toList());
    }

    public ClientDTO getClientById(Long clientId) {
        Long userId = getCurrentUserId();
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        // Verify client belongs to current user
        if (!client.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        return buildClientDTO(client);
    }

    @Transactional
    public PreQualificationResponseDTO performPreQualification(Long clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        Long userId = getCurrentUserId();
        if (!client.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        // TODO: Implement actual pre-qualification logic based on bonus rules
        // For now, return a mock response
        PreQualificationResponseDTO response = PreQualificationResponseDTO.builder()
                .clientId(clientId)
                .bbpStatus("ELIGIBLE")
                .sustainableBonusStatus("NOT_ELIGIBLE")
                .integratorBonusStatus("ELIGIBLE")
                .techoPropioStatus("ELIGIBLE")
                .recomendacion("Cliente elegible para BBP e Integrador")
                .isEligible(true)
                .build();

        // Update client with pre-qualification results
        client.setBbpStatus(response.getBbpStatus());
        client.setSustainableBonusStatus(response.getSustainableBonusStatus());
        client.setIntegratorBonusStatus(response.getIntegratorBonusStatus());
        client.setTechoPropioStatus(response.getTechoPropioStatus());
        client.setRecomendacion(response.getRecomendacion());
        clientRepository.save(client);

        return response;
    }

    public PreQualificationResponseDTO performPreQualification(PreQualificationRequestDTO request) {
        // Variables de estado
        String bbpStatus = "NOT_ELIGIBLE";
        String sustainableBonusStatus = "REQUIRES_PROPERTY_EVALUATION";
        String integratorBonusStatus = "NOT_ELIGIBLE";
        String techoPropioStatus = "NOT_ELIGIBLE";
        StringBuilder recomendacion = new StringBuilder();
        boolean isEligible = false;

        // Constantes
        BigDecimal techoPropioMaxIncome;
        try {
            techoPropioMaxIncome = configurationService.getNumericValue("BFH_MAX_MONTHLY_INCOME");
        } catch (Exception e) {
            // Fallback por seguridad si no encuentra el valor en BD
            techoPropioMaxIncome = new BigDecimal("3715");
        }
        final BigDecimal MIN_INCOME_FOR_CREDIT = new BigDecimal("1500"); // Umbral referencial del banco

        // --- 1. EVALUACIÓN TECHO PROPIO (Prioridad para ingresos bajos) ---
        boolean cumpleNoPropiedad = !request.getIsOwnerOfAnotherProperty();
        boolean cumpleNoApoyo = !request.getHasReceivedPreviousSupport();
        boolean ingresosBajos = request.getFamilyNetIncome().compareTo(techoPropioMaxIncome) <= 0;

        if (ingresosBajos) {
            if (cumpleNoPropiedad && cumpleNoApoyo) {
                techoPropioStatus = "ELIGIBLE";
                isEligible = true;
                recomendacion.append("✅ **Programa Techo Propio (AVN):**\n")
                        .append("   ¡Excelentes noticias! Cumples con todos los requisitos para el Bono Familiar Habitacional (BFH). ")
                        .append("Este es el subsidio más alto del estado (aprox. S/ 44,000) para comprar tu primera vivienda nueva.\n\n");
            } else {
                recomendacion.append("⚠️ **Observación Techo Propio:**\n   Tus ingresos califican, pero ");
                if (!cumpleNoPropiedad) recomendacion.append("figuras como propietario de otra vivienda. ");
                if (!cumpleNoApoyo) recomendacion.append("ya has recibido apoyo del Estado anteriormente. ");
                recomendacion.append("Esto te impide acceder al BFH gratuito.\n\n");
            }
        }

        // --- 2. EVALUACIÓN MIVIVIENDA / CRÉDITO HIPOTECARIO ---
        // Si gana más del límite de TP, o si gana menos pero quiere una casa más cara (evaluamos capacidad general)
        if (request.getFamilyNetIncome().compareTo(MIN_INCOME_FOR_CREDIT) >= 0) {
            isEligible = true; // Es sujeto de crédito
            recomendacion.append("🏦 **Nuevo Crédito MiVivienda:**\n");

            if (cumpleNoPropiedad) {
                bbpStatus = "ELIGIBLE";
                recomendacion.append("   Calificas para un crédito hipotecario con el **Bono del Buen Pagador (BBP)**. ")
                        .append("Este bono reducirá tu cuota inicial o el monto total a financiar. ")
                        .append("Puedes buscar viviendas de mayor valor (hasta S/ 488,800 aprox).\n\n");
            } else {
                recomendacion.append("   Cuentas con capacidad para un crédito hipotecario MiVivienda. ")
                        .append("Sin embargo, al tener propiedad registrada, no aplicas al bono (BBP), ")
                        .append("pero sí puedes beneficiarte de las tasas preferenciales del fondo.\n\n");
            }
        } else {
            recomendacion.append("📉 **Análisis Financiero:**\n")
                    .append("   Tus ingresos reportados podrían ser insuficientes para calificar a un crédito hipotecario bancario en este momento. ")
                    .append("Te sugerimos sumar ingresos con un cónyuge o aval para mejorar tu perfil.\n\n");
        }

        // --- 3. BONOS ADICIONALES (Acumulables) ---
        boolean esAdultoMayor = request.getAge() >= 60;
        boolean tieneDiscapacidad = request.getConadisCardNumber() != null && !request.getConadisCardNumber().trim().isEmpty();

        if (isEligible && (esAdultoMayor || tieneDiscapacidad)) {
            integratorBonusStatus = "ELIGIBLE";
            recomendacion.append("➕ **Bono Integrador:**\n   ");
            if (esAdultoMayor) recomendacion.append("Por ser adulto mayor (60+ años), ");
            if (tieneDiscapacidad) recomendacion.append("Por contar con carnet de CONADIS, ");
            recomendacion.append("accedes a un descuento adicional sobre el valor de la vivienda. ¡Asegúrate de mencionarlo al banco!\n\n");
        }

        // --- 4. BONO VERDE (Informativo) ---
        if (isEligible) {
            recomendacion.append("🌱 **Tip de Ahorro:**\n   Si eliges un proyecto certificado como 'MiVivienda Verde' (Sostenible), ")
                    .append("recibirás un bono adicional de aproximadamente S/ 5,000 y una tasa de interés preferencial.");
        }

        return PreQualificationResponseDTO.builder()
                .clientId(null)
                .bbpStatus(bbpStatus)
                .sustainableBonusStatus(sustainableBonusStatus)
                .integratorBonusStatus(integratorBonusStatus)
                .techoPropioStatus(techoPropioStatus)
                .recomendacion(recomendacion.toString().trim())
                .isEligible(isEligible)
                .build();
    }

    @Transactional
    public ClientDTO updateClient(Long clientId, CreateClientRequestDTO request) {
        Long userId = getCurrentUserId();
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        if (!client.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        // Update holder person
        Person holder = personRepository.findById(client.getHolderId())
                .orElseThrow(() -> new RuntimeException("Holder person not found"));
        updatePerson(holder, request.getHolder());
        personRepository.save(holder);

        // Update or create spouse person based on marital status
        Person spouse = null;
        Person currentHolder = personRepository.findById(client.getHolderId())
                .orElseThrow(() -> new RuntimeException("Holder person not found"));

        if (shouldHaveSpouse(currentHolder.getMaritalStatusId())) {
            if (request.getSpouse() != null) {
                if (client.getSpouseId() != null) {
                    spouse = personRepository.findById(client.getSpouseId()).orElse(null);
                    if (spouse != null) {
                        updatePerson(spouse, request.getSpouse());
                    } else {
                        spouse = createPerson(request.getSpouse());
                    }
                } else {
                    spouse = createPerson(request.getSpouse());
                }
                personRepository.save(spouse);
            } else {
                throw new RuntimeException("Spouse information is required for married or cohabiting clients");
            }
        } else {
            // If marital status changed to single/divorced/widowed, remove spouse if exists
            if (client.getSpouseId() != null) {
                // Optional: could delete the spouse person record, but for now just disassociate
                client.setSpouseId(null);
            }
        }

        // Update client
        client.setFundSourceId(request.getFundSourceId());
        client.setFamilyNetIncome(request.getFamilyNetIncome());
        client.setAppliesForIntegratorBonus(request.getAppliesForIntegratorBonus());
        client.setConadisCardNumber(request.getConadisCardNumber());
        client.setIsOwnerOfAnotherProperty(request.getIsOwnerOfAnotherProperty());
        client.setReceivedPreviousSupport(request.getReceivedPreviousSupport());
        client.setSpouseId(spouse != null ? spouse.getId() : null);

        client = clientRepository.save(client);

        // Update pre-qualification based on new data
        updateClientPreQualification(client, holder, spouse);

        return buildClientDTO(client, holder, spouse);
    }

    @Transactional
    public void deleteClient(Long clientId) {
        Long userId = getCurrentUserId();
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        if (!client.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        clientRepository.delete(client);
    }

    private Person createPerson(CreatePersonRequestDTO request) {
        return Person.builder()
                .documentTypeId(request.getDocumentTypeId())
                .maritalStatusId(request.getMaritalStatusId())
                .educationLevelId(request.getEducationLevelId())
                .economicActivityId(request.getEconomicActivityId())
                .nombres(request.getNombres())
                .apellidos(request.getApellidos())
                .documentNumber(request.getDocumentNumber())
                .birthDate(request.getBirthDate())
                .correo(request.getCorreo())
                .telefono(request.getTelefono())
                .direccion(request.getDireccion())
                .profesion(request.getProfesion())
                .monthlyNetIncome(request.getMonthlyNetIncome())
                .build();
    }

    private ClientDTO buildClientDTO(Client client) {
        Person holder = personRepository.findById(client.getHolderId())
                .orElseThrow(() -> new RuntimeException("Holder person not found"));

        Person spouse = null;
        if (client.getSpouseId() != null) {
            spouse = personRepository.findById(client.getSpouseId()).orElse(null);
        }

        return buildClientDTO(client, holder, spouse);
    }

    private ClientDTO buildClientDTO(Client client, Person holder, Person spouse) {
        return ClientDTO.builder()
                .id(client.getId())
                .userId(client.getUserId())
                .holder(modelMapper.map(holder, PersonDTO.class))
                .spouse(spouse != null ? modelMapper.map(spouse, PersonDTO.class) : null)
                .fundSourceId(client.getFundSourceId())
                .familyNetIncome(client.getFamilyNetIncome())
                .appliesForIntegratorBonus(client.getAppliesForIntegratorBonus())
                .conadisCardNumber(client.getConadisCardNumber())
                .isOwnerOfAnotherProperty(client.getIsOwnerOfAnotherProperty())
                .receivedPreviousSupport(client.getReceivedPreviousSupport())
                .bbpStatus(client.getBbpStatus())
                .sustainableBonusStatus(client.getSustainableBonusStatus())
                .integratorBonusStatus(client.getIntegratorBonusStatus())
                .techoPropioStatus(client.getTechoPropioStatus())
                .recomendacion(client.getRecomendacion())
                .registrationDate(client.getRegistrationDate())
                .build();
    }

    private void updatePerson(Person person, CreatePersonRequestDTO request) {
        person.setDocumentTypeId(request.getDocumentTypeId());
        person.setMaritalStatusId(request.getMaritalStatusId());
        person.setEducationLevelId(request.getEducationLevelId());
        person.setEconomicActivityId(request.getEconomicActivityId());
        person.setNombres(request.getNombres());
        person.setApellidos(request.getApellidos());
        person.setDocumentNumber(request.getDocumentNumber());
        person.setBirthDate(request.getBirthDate());
        person.setCorreo(request.getCorreo());
        person.setTelefono(request.getTelefono());
        person.setDireccion(request.getDireccion());
        person.setProfesion(request.getProfesion());
        person.setMonthlyNetIncome(request.getMonthlyNetIncome());
    }

    private void performInitialPreQualification(Client client, Person holder, Person spouse) {
        // Calculate age from birth date
        int age = holder.getBirthDate() != null ?
                java.time.LocalDate.now().getYear() - holder.getBirthDate().getYear() : 0;

        // Calculate family net income
        BigDecimal familyNetIncome = holder.getMonthlyNetIncome() != null ? holder.getMonthlyNetIncome() : BigDecimal.ZERO;
        if (spouse != null && spouse.getMonthlyNetIncome() != null) {
            familyNetIncome = familyNetIncome.add(spouse.getMonthlyNetIncome());
        }

        // Perform pre-qualification with initial data
        PreQualificationRequestDTO preQualRequest = PreQualificationRequestDTO.builder()
                .monthlyIncome(holder.getMonthlyNetIncome())
                .familyNetIncome(familyNetIncome)
                .age(age)
                .appliesForIntegratorBonus(client.getAppliesForIntegratorBonus())
                .isOwnerOfAnotherProperty(client.getIsOwnerOfAnotherProperty())
                .hasReceivedPreviousSupport(client.getReceivedPreviousSupport())
                .conadisCardNumber(client.getConadisCardNumber())
                .build();

        PreQualificationResponseDTO preQualResponse = performPreQualification(preQualRequest);

        // Update client with pre-qualification results
        client.setBbpStatus(preQualResponse.getBbpStatus());
        client.setSustainableBonusStatus(preQualResponse.getSustainableBonusStatus());
        client.setIntegratorBonusStatus(preQualResponse.getIntegratorBonusStatus());
        client.setTechoPropioStatus(preQualResponse.getTechoPropioStatus());
        client.setRecomendacion(preQualResponse.getRecomendacion());

        clientRepository.save(client);
    }

    private void updateClientPreQualification(Client client, Person holder, Person spouse) {
        // Calculate age from birth date
        int age = holder.getBirthDate() != null ?
                java.time.LocalDate.now().getYear() - holder.getBirthDate().getYear() : 0;

        // Calculate family net income
        BigDecimal familyNetIncome = holder.getMonthlyNetIncome() != null ? holder.getMonthlyNetIncome() : BigDecimal.ZERO;
        if (spouse != null && spouse.getMonthlyNetIncome() != null) {
            familyNetIncome = familyNetIncome.add(spouse.getMonthlyNetIncome());
        }

        // Perform pre-qualification with updated data
        PreQualificationRequestDTO preQualRequest = PreQualificationRequestDTO.builder()
                .monthlyIncome(holder.getMonthlyNetIncome())
                .familyNetIncome(familyNetIncome)
                .age(age)
                .appliesForIntegratorBonus(client.getAppliesForIntegratorBonus())
                .isOwnerOfAnotherProperty(client.getIsOwnerOfAnotherProperty())
                .hasReceivedPreviousSupport(client.getReceivedPreviousSupport())
                .conadisCardNumber(client.getConadisCardNumber())
                .build();

        PreQualificationResponseDTO preQualResponse = performPreQualification(preQualRequest);

        // Update client with new pre-qualification results
        client.setBbpStatus(preQualResponse.getBbpStatus());
        client.setSustainableBonusStatus(preQualResponse.getSustainableBonusStatus());
        client.setIntegratorBonusStatus(preQualResponse.getIntegratorBonusStatus());
        client.setTechoPropioStatus(preQualResponse.getTechoPropioStatus());
        client.setRecomendacion(preQualResponse.getRecomendacion());

        clientRepository.save(client);
    }

    private boolean shouldHaveSpouse(Long maritalStatusId) {
        // Assuming marital status IDs:
        // 1 = Soltero, 2 = Casado, 3 = Divorciado, 4 = Viudo, 5 = Conviviente
        // Only married (2) and cohabiting (5) require spouse information
        return maritalStatusId != null && (maritalStatusId == 2L || maritalStatusId == 5L);
    }

    private void validateBirthDate(java.time.LocalDate birthDate) {
        if (birthDate == null) {
            throw new RuntimeException("Birth date is required");
        }

        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.Period age = java.time.Period.between(birthDate, today);

        if (age.getYears() < 18) {
            throw new RuntimeException("Person must be at least 18 years old");
        }
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof com.simucredito.iam.domain.model.User) {
            com.simucredito.iam.domain.model.User user = (com.simucredito.iam.domain.model.User) authentication.getPrincipal();
            return user.getId();
        }

        throw new RuntimeException("Usuario no autenticado o sesión inválida");
    }
}