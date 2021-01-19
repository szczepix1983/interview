package com.szczepix.nyskaflat.services;

import com.szczepix.nyskaflat.dao.IShedulersRepository;
import com.szczepix.nyskaflat.dto.user.UserCleaningDto;
import com.szczepix.nyskaflat.dto.user.UserCleaningStateDto;
import com.szczepix.nyskaflat.entities.RoomsEntity;
import com.szczepix.nyskaflat.entities.ShedulersEntity;
import com.szczepix.nyskaflat.security.model.UserSession;
import com.szczepix.nyskaflat.utils.SchedulerComparator;
import com.szczepix.nyskaflat.utils.SchedulerConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CalendarService implements ICalendarService {

    private static final Logger logger = LoggerFactory.getLogger(CalendarService.class);

    private final IRoomsService roomsService;
    private final IShedulersRepository shedulersRepository;

    @Autowired
    public CalendarService(final IShedulersRepository shedulersRepository, final IRoomsService roomsService) {
        this.shedulersRepository = shedulersRepository;
        this.roomsService = roomsService;
    }

    @Override
    public List<UserCleaningDto> getCalendars() {
        Optional<ShedulersEntity> lastEntity = shedulersRepository.findTopByOrderByIdDesc();
        if (lastEntity.isPresent()) {
            updateCalendarByLastDate(lastEntity.get().getShedulerId());

            List<UserCleaningDto> sheduler = new ArrayList<>();
            for (RoomsEntity entity : roomsService.getAll()) {
                sheduler.addAll(shedulersRepository.findByRoomId("" + entity.getId())
                        .stream()
                        .map(schedulerEntity -> convertToUserCleaning(schedulerEntity, entity, false))
                        .collect(Collectors.toList()));
            }
            return sheduler.stream()
                    .sorted(new SchedulerComparator().reversed())
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();

    }

    @Override
    public List<UserCleaningDto> getCalendar(UserSession user) {
        Optional<RoomsEntity> roomsEntity = roomsService.getRoom(user.getEntity().getRoomId());
        Optional<ShedulersEntity> lastEntity = shedulersRepository.findTopByOrderByIdDesc();
        if (roomsEntity.isPresent() && lastEntity.isPresent()) {
            updateCalendarByLastDate(lastEntity.get().getShedulerId());

            List<UserCleaningDto> sheduler = new ArrayList<>();
            for (RoomsEntity entity : roomsService.getAll()) {
                sheduler.addAll(shedulersRepository.findByRoomId("" + entity.getId())
                        .stream()
                        .map(schedulerEntity -> convertToUserCleaning(schedulerEntity, entity, entity.getId().equals(roomsEntity.get().getId())))
                        .collect(Collectors.toList()));
            }
            return sheduler.stream()
                    .sorted(new SchedulerComparator().reversed())
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private void updateCalendarByLastDate(final String shedulerId) {
        logger.info("Sprawdzamy kalendarz dla ostatniego wpisu z " + shedulerId);

        final boolean validCalendar = SchedulerConverter.isCalendarValid(
                SchedulerConverter.calculateEndDate(shedulerId));

        if (!validCalendar) {
            List<RoomsEntity> rooms = roomsService.getAll();
            for (RoomsEntity roomsEntity : rooms) {
                String nextShedulerId = SchedulerConverter.calculateNextDate(shedulerId, (1 + rooms.indexOf(roomsEntity)));
                ShedulersEntity entity = new ShedulersEntity();
                entity.setRoomId("" + roomsEntity.getId());
                entity.setKitchen(0);
                entity.setBathroom(0);
                entity.setToilet(0);
                entity.setLivingroom(0);
                entity.setShedulerId(nextShedulerId);
                shedulersRepository.save(entity);

                logger.info("Dodajemy kalendarz dla pokoju " + roomsEntity.getName() + " z wpisem " + nextShedulerId);
            }
        }
    }

    @Override
    public boolean update(UserCleaningDto userCleaningDto) {
        Optional<ShedulersEntity> entity = shedulersRepository.findById(userCleaningDto.id);
        if (entity.isPresent()) {
            entity.get().setBathroom(userCleaningDto.state.bathroom ? 1 : 0);
            entity.get().setKitchen(userCleaningDto.state.kitchen ? 1 : 0);
            entity.get().setLivingroom(userCleaningDto.state.livingroom ? 1 : 0);
            entity.get().setToilet(userCleaningDto.state.toilet ? 1 : 0);
            shedulersRepository.save(entity.get());
            return true;
        }
        return false;
    }

    private UserCleaningDto convertToUserCleaning(final ShedulersEntity schedulerEntity, final RoomsEntity roomsEntity, final boolean editable) {
        final UserCleaningStateDto state = new UserCleaningStateDto(schedulerEntity.getLivingroom().equals(1),
                schedulerEntity.getBathroom().equals(1),
                schedulerEntity.getToilet().equals(1),
                schedulerEntity.getKitchen().equals(1));
        return new UserCleaningDto(schedulerEntity.getId(),
                roomsEntity.getName(),
                schedulerEntity.getShedulerId(),
                SchedulerConverter.calculateEndDate(schedulerEntity.getShedulerId()),
                editable,
                state);
    }
}
