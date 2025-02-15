package com.cywalk.spring_boot.Users;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import com.cywalk.spring_boot.Organizations.OnlineUserService;
import com.cywalk.spring_boot.Organizations.OrganizationOnlineUsersWebSocket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PeopleService {

    // I love comptime
    public static final int AMOUNT_LEAGUES = League.values().length;

    @Autowired
    private PeopleRepository peopleRepository;

    @Autowired
    private OnlineUserService onlineUserService;

    @Autowired
    private UserModelRepository userModelRepository;

    @Autowired
    private UserRequestRepository userRequestRepository;

    private final Logger logger = LoggerFactory.getLogger(PeopleService.class);

    public PeopleService() {}

    @Transactional
    public Optional<People> createUser(People user) {
        try {
            if (peopleRepository.findByUsername(user.getUsername()).isPresent()) {
                logger.warn("Issue: username already in use");
                return Optional.empty();
            }
            People savedUser = peopleRepository.save(user);
            logger.info("User saved successfully: {}", savedUser);
            return Optional.of(savedUser);
        } catch (Exception e) {
            logger.error("Error saving user: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }


    @Transactional
    public void saveUserRequest(UserRequest userRequest) {
        userRequestRepository.save(userRequest);
    }

    @Transactional
    public Optional<People> getUserByUsername(String username) {
        return peopleRepository.findByUsername(username);
    }

    @Transactional
    public People saveUser(People people) {
        return peopleRepository.save(people);
    }

    /**
     * Delete a user in the database based off of the passed in username
     * @param name the username of the user to delete
     */
    @Transactional
    public void deleteUserByName(String name) {
        Optional<People> peopleResult = getUserByUsername(name);
        if (peopleResult.isPresent()) {
            userModelRepository.deleteByPeople(peopleResult.get());
            peopleRepository.deleteByUsername(name);
        }
    }

    /**
     * generates an authentication key that the front end should keep track of for.
     * This will return -1 if the id does now work
     *
     * @param username the People key in the entity
     * @return a valid auth key
     */
    @Transactional
    public Optional<Long> generateAuthKey(String username) {
        Optional<People> userResult = peopleRepository.findByUsername(username);
        if (userResult.isPresent()) {

            UserModel model = new UserModel(userResult.get());
            userModelRepository.save(model);

            People user = userResult.get();
            if (user.getOrganization() != null) {
                Long orgId = user.getOrganization().getId();
                onlineUserService.userLoggedIn(username, orgId);

                OrganizationOnlineUsersWebSocket.broadcastOnlineUsers(
                        orgId, onlineUserService.getOnlineUsers(orgId));
            }

            return Optional.of(model.getId());
        } else {

            return Optional.empty();
        }
    }

    /**
     * verifies whether a key is correct or not.
     * If it isn't valid the return wil be empty.
     * If the key is valid it will return the user object.
     *
     * @param key the secret temporary key
     * @return A user if it's available
     */
    @Transactional
    public Optional<People> getUserFromKey(Long key) {
        Optional<UserModel> userModelResult = userModelRepository.findById(key);

        if (userModelResult.isPresent()) {
            UserModel userModel = userModelResult.get();
            return Optional.of(userModel.getUser());
        }
        else {
            logger.warn("key did not find a value. key passed in: {}", key);
            return Optional.empty();
        }
    }


    /**
     * Login a user
     * @param request the username password combination
     * @return a key to be used throughout the session until the user logs out
     */
    @Transactional
    public ResponseEntity<Map<String,Object>> login(UserRequest request) {
        Optional<UserRequest> userReqOpt = userRequestRepository.findByUsername(request.getUsername());
        if (userReqOpt.isPresent()) {
            UserRequest userReq = userReqOpt.get();
            if (userReq.getPassword().equals(request.getPassword())) {
                Optional<Long> toReturn = generateAuthKey(request.getUsername());
                if (toReturn.isPresent()) {
                    Optional<People> userOpt = getUserByUsername(request.getUsername());
                    if (userOpt.isPresent()) {
                        String userType = userOpt.get().getUserType();
                        if (userType == null || userType.isEmpty()) {
                            userType = "USER";
                        }
                        Map<String, Object> responseBody = new HashMap<>();
                        responseBody.put("id", toReturn.get());
                        responseBody.put("type", userType);
                        return ResponseEntity.ok(responseBody);
                    } else {
                        return ResponseEntity.notFound().build();
                    }
                }
            } else {
                logger.warn("Password incorrect for user: {}", request.getUsername());
            }
        } else {
            logger.warn("User not found: {}", request.getUsername());
        }
        return ResponseEntity.notFound().build();
    }


    @Transactional
    public ResponseEntity<Void> logout(Long key) {
        Optional<UserModel> toDelete = userModelRepository.findById(key);
        if (toDelete.isPresent()) {
            userModelRepository.deleteById(key);
            return ResponseEntity.ok().build();
        }
        else {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * @return all users in the database
     */
    public List<People> getAllPeople() {
        return peopleRepository.findAll();
    }

    /**
     * updates the bio of a user
     */
    public People updateBio(People people, String bio) {
        people.setBio(bio);
        return peopleRepository.save(people);
    }

    /**
     * Updates the username of a user
     */
    public People updateUsername(People people, String newUsername) {
        peopleRepository.delete(people);
        people.setUsername(newUsername);
        return peopleRepository.save(people);
    }

    /**
     * Updates the email of a user
     */
    public People updateEmail(People people, String newEmail) {
        people.setEmail(newEmail);
        return peopleRepository.save(people);
    }

    public Boolean isUserLoggedIn(People people) {
        return userModelRepository.findByPeople(people).isPresent();
    }

    /**
     * updates and returns the league that the current user is in.
     * This function calculates the user's global ranking relative to everyone else and then returns that
     * @param username the username of the user.
     *                 THIS FUNCTION SHOULD ONLY BE CALLED WHEN WE KNOW FOR SURE THAT THE USER FOR SAID USERNAME EXISTS
     * @return the league that they are in
     */
    /*
    public League updateLeagueForUser(String username) {
        Optional<Long> rankingResult = ;
        long ranking = 1;
        if (rankingResult.isPresent()) {
            ranking = rankingResult.get();
        }
        int amountOfUsers = getAllPeople().size();
        // ranking as a percent corresponds to the league
        double pointInRank = (double) ranking / (double) amountOfUsers;
        // this number times the amount of leagues gives the league a user is in
        return League.values()[(int) pointInRank * AMOUNT_LEAGUES];
    }
     */



}
