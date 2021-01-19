package com.szczepix.nyskaflat.services;

import com.szczepix.nyskaflat.dao.ICostsRepository;
import com.szczepix.nyskaflat.dao.IPaymentsRepository;
import com.szczepix.nyskaflat.dto.admin.AdminPaymentDto;
import com.szczepix.nyskaflat.dto.user.UserPaymentDto;
import com.szczepix.nyskaflat.dto.user.UserPaymentPriceDto;
import com.szczepix.nyskaflat.entities.CostsEntity;
import com.szczepix.nyskaflat.entities.NeighboursEntity;
import com.szczepix.nyskaflat.entities.PaymentsEntity;
import com.szczepix.nyskaflat.entities.RoomsEntity;
import com.szczepix.nyskaflat.utils.CostConverter;
import com.szczepix.nyskaflat.utils.CostsComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PaymentService implements IPaymentService {

    private final ICostsRepository costsRepository;
    private final IPaymentsRepository paymentsRepository;
    private final IRoomsService roomsService;

    private final DecimalFormat df = new DecimalFormat("0.00");

    @Autowired
    public PaymentService(final ICostsRepository costsRepository, final IPaymentsRepository paymentsRepository, final IRoomsService roomsService) {
        this.costsRepository = costsRepository;
        this.paymentsRepository = paymentsRepository;
        this.roomsService = roomsService;
    }

    @Override
    public Optional<UserPaymentDto> getActivePaymentByUser(final NeighboursEntity neighboursEntity) {
        return getPaymentsByUser(neighboursEntity).stream()
                .filter(dto -> !dto.accepted)
                .findFirst();
    }

    @Override
    public List<UserPaymentDto> getPaymentsByUser(final NeighboursEntity neighboursEntity) {
        RoomsEntity roomsEntity = roomsService.getRoom(neighboursEntity.getRoomId()).get();
        return costsRepository.findByCostIdEndingWith("_" + roomsEntity.getId())
                .stream()
                .map(costsEntity -> convertToUserPayment(costsEntity, roomsEntity))
                .sorted(new CostsComparator().reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<UserPaymentDto> getAllPayments() {
        List<UserPaymentDto> costsEntities = new ArrayList<>();
        for (RoomsEntity roomsEntity : roomsService.getAll()) {
            costsEntities.addAll(costsRepository.findByCostIdEndingWith("_" + roomsEntity.getId())
                    .stream()
                    .map(costsEntity -> convertToUserPayment(costsEntity, roomsEntity))
                    .collect(Collectors.toList()));
        }
        return costsEntities.stream()
                .sorted(new CostsComparator().reversed())
                .collect(Collectors.toList());
    }

    @Override
    public boolean addPayment(AdminPaymentDto adminPaymentDto) {
        PaymentsEntity paymentsEntity = findPaymentById(adminPaymentDto.paymentId);
        if (Objects.nonNull(paymentsEntity.getId())) {
            return false;
        }
        paymentsEntity.setMedia(adminPaymentDto.media);
        paymentsEntity.setEnergy(adminPaymentDto.energy);
        paymentsEntity.setInternet(adminPaymentDto.internet);
        paymentsEntity.setPurchases(adminPaymentDto.purchases);
        paymentsEntity.setPaymentId(adminPaymentDto.paymentId);

        paymentsRepository.save(paymentsEntity);

        for (RoomsEntity roomsEntity : roomsService.getAll()) {
            float roomPrice = roomsEntity.getPrice()
                    + getPurchasesPrice(paymentsEntity, roomsEntity)
                    + getMediaPrice(paymentsEntity, roomsEntity)
                    + getEnergyPrice(paymentsEntity, roomsEntity)
                    + getInternetPrice(paymentsEntity, roomsEntity);
            CostsEntity costsEntity = new CostsEntity();
            costsEntity.setAccepted(0);
            costsEntity.setCostId(paymentsEntity.getPaymentId() + "_" + roomsEntity.getId());
            costsEntity.setPrice(Float.parseFloat(df.format(roomPrice)));

            costsRepository.save(costsEntity);
        }
        return true;
    }

    @Override
    public boolean update(final UserPaymentDto userPaymentDto) {
        Optional<CostsEntity> entity = costsRepository.findById(userPaymentDto.id);
        if(entity.isPresent()){
            entity.get().setAccepted(userPaymentDto.accepted ? 1 : 0);
            costsRepository.save(entity.get());
            return true;
        }
        return false;
    }

    private UserPaymentDto convertToUserPayment(final CostsEntity costsEntity, final RoomsEntity roomsEntity) {
        final PaymentsEntity paymentsEntity = findPaymentById(CostConverter.extractPaymentId(costsEntity.getCostId()));
        final UserPaymentPriceDto paymentPriceDto = new UserPaymentPriceDto(
                df.format(getMediaPrice(paymentsEntity, roomsEntity)),
                df.format(getEnergyPrice(paymentsEntity, roomsEntity)),
                df.format(getInternetPrice(paymentsEntity, roomsEntity)),
                df.format(getPurchasesPrice(paymentsEntity, roomsEntity)),
                df.format(costsEntity.getPrice()));
        return new UserPaymentDto(
                costsEntity.getId(),
                roomsEntity.getName(),
                CostConverter.extractMonth(costsEntity.getCostId()),
                CostConverter.extractYear(costsEntity.getCostId()),
                paymentPriceDto,
                costsEntity.getAccepted().equals(1));
    }

    private Float getMediaPrice(final PaymentsEntity paymentsEntity, final RoomsEntity roomsEntity) {
        return ((float) paymentsEntity.getMedia() * (float) roomsEntity.getMultiplier()) / 100;
    }

    private Float getEnergyPrice(final PaymentsEntity paymentsEntity, final RoomsEntity roomsEntity) {
        return ((float) paymentsEntity.getEnergy() * (float) roomsEntity.getMultiplier()) / 100;
    }

    private Float getInternetPrice(final PaymentsEntity paymentsEntity, final RoomsEntity roomsEntity) {
        return ((float) paymentsEntity.getInternet() * (float) roomsEntity.getMultiplier()) / 100;
    }

    private Float getPurchasesPrice(final PaymentsEntity paymentsEntity, final RoomsEntity roomsEntity) {
        return paymentsEntity.getPurchases() * roomsEntity.getPurchuseMultiplier();
    }

    private PaymentsEntity findPaymentById(String paymentId) {
        final Optional<PaymentsEntity> payments = paymentsRepository.findByPaymentId(paymentId);
        return payments.orElseGet(PaymentsEntity::new);
    }
}
