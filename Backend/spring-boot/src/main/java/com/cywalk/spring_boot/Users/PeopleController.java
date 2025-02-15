package com.cywalk.spring_boot.Users;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.cywalk.spring_boot.Organizations.OnlineUserService;
import com.cywalk.spring_boot.Organizations.OrganizationOnlineUsersWebSocket;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import static com.cywalk.spring_boot.Friends.FriendController.asJsonString;

@RestController
@RequestMapping("/users")
public class PeopleController {

    @Autowired
    private PeopleService peopleService;
    @Autowired
    private UserModelRepository userModelRepository;

    @Autowired
    private OnlineUserService onlineUserService;

    private Logger logger = LoggerFactory.getLogger(PeopleController.class);

    private static String directory = "/target/profileImages/";

    @Autowired
    private ImageRepository imageRepository;

    @Operation(summary = "retrieves a People model for a corresponding username in the database")
    @ApiResponse(useReturnTypeSchema = true)
    @GetMapping("/username/{username}")
    public Optional<People> getUserByUsername(@PathVariable @Parameter(name = "username", description = "the username of a user used when signed in", example = "ckugel") String username) {
        return peopleService.getUserByUsername(username);
    }

    @Operation(summary = "gets all of the users", description = "Retrieves all of the users from the database and returns those entitys")
    @ApiResponse(useReturnTypeSchema = true)
    @GetMapping
    public List<People> getAllUsers() {
        return peopleService.getAllPeople();
    }

    @Operation(summary = "creates a new user with a person schema")
    @PostMapping
    public Optional<People> createUser(@RequestBody People people) {
        return peopleService.createUser(people);
    }

    @GetMapping("/{id}")
    public Optional<People> getUserBySessionKey(@PathVariable Long id) {
        return peopleService.getUserFromKey(id);
    }

    @DeleteMapping("/username/{username}")
    public void deleteUserByName(@PathVariable String username) {
        peopleService.deleteUserByName(username);
    }

    /**
     * @param userRequest username password combo
     * @return the key to be used during the session
     */
    @Operation(summary = "Log in a user with the corresponding user request")
    @PutMapping
    public ResponseEntity<Map<String,Object>> login(@RequestBody UserRequest userRequest) {
        return peopleService.login(userRequest);
    }


    /**
     * log out a user and delete the cooresponding usermodel from the database
     *
     * @param key the key that was used for the login session
     * @return the
     */
    @Operation(summary = "Log out an instance of a user")
    @ApiResponses( value = {
            @ApiResponse(responseCode = "200", description = "successfully logged out the user"),
            @ApiResponse(responseCode = "404", description = "No logged in users found")
    })
    @DeleteMapping("/{key}")
    public ResponseEntity<Void> logout(@PathVariable @Parameter(name = "key", description = "user key", example = "1") Long key) {
        Optional<UserModel> toDelete = userModelRepository.findById(key);
        if (toDelete.isPresent()) {
            People user = toDelete.get().getUser();
            userModelRepository.deleteById(key);

            // Update online users
            if (user.getOrganization() != null) {
                Long orgId = user.getOrganization().getId();
                onlineUserService.userLoggedOut(user.getUsername(), orgId);
                // Broadcast update
                OrganizationOnlineUsersWebSocket.broadcastOnlineUsers(
                        orgId, onlineUserService.getOnlineUsers(orgId));
            }

            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Log out all instances of a user including the current one
     * @param key session key
     * @return a successful key
     */
    @Operation(summary = "Log out all instances of a user including the current one")
    @ApiResponses( value = {
            @ApiResponse(responseCode = "200", description = "successfully logged out any logged in users"),
            @ApiResponse(responseCode = "404", description = "No logged in users found")
    })
    @DeleteMapping("/logins/{key}")
    public ResponseEntity<Void> logoutAllOfUser(@PathVariable Long key) {
       ResponseEntity<List<UserModel>> result = getActiveSessions(key);
       if (result.getStatusCode().value() == 200) {
           List<UserModel> elements = result.getBody();
           if (elements == null) {
               return ResponseEntity.notFound().build();
           }
           else {
               for (UserModel userModel : elements) {
                   peopleService.logout(userModel.getId());
               }
               return ResponseEntity.ok().build();
           }
       }
       else {
           return ResponseEntity.status(result.getStatusCode()).build();
       }


    }

    /**
     * Gets all the current UserModels in the database. intended to be wiped out with {@link #logoutAllOfUser(Long)}
     *
     * Bad request if there is no user corresponding to the passed in key
     * not found if there are no other active sessions for the user
     *
     * @param key the session key from {@link #login(UserRequest)}
     * @return all the sessions of a user
     */
    @Operation(summary = "Gets all the current User Models in the database. Intended to be cleared using logoutAllOfUser")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Got all active sessions"),
            @ApiResponse(responseCode = "404", description = "No logged in users found")
    })
    @GetMapping("/logins/{key}")
    public ResponseEntity<List<UserModel>> getActiveSessions(@PathVariable Long key) {
        Optional<UserModel> userRequest = userModelRepository.findById(key);
        if (userRequest.isPresent()) {
            List<UserModel> current = userModelRepository.findAllByPeople(userRequest.get().getUser());
            if (current.isEmpty()) {
                return ResponseEntity.notFound().build();
            } else {
                return ResponseEntity.ok(current);
            }
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "upload a photo to be used as the profile image", description = "uploads a new profile image, replaces one if it currently exists")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "successfully uploaded the image"),
            @ApiResponse(responseCode = "512", description = "could not write image on server end"),
            @ApiResponse(responseCode = "404", description = "user not found")
    })
    @PostMapping("/image/{key}")
    public ResponseEntity<Void> uploadImage(
            @RequestParam("image")
            @Parameter(name = "imageFile", description = "The image as a file? look at springboot tutorials to send")
            MultipartFile imageFile,
            @PathVariable @Parameter(name = "key", description = "The user's session key", example = "/1")
            Long key
            ) {

        Optional<People> peopleResult = peopleService.getUserFromKey(key);
        if (peopleResult.isEmpty()) {
            return ResponseEntity.status(404).build();
        }

        People user = peopleResult.get();

        try {
            File destinationFile = new File(directory + File.separator + user.getUsername() + imageFile.getOriginalFilename());
            imageFile.transferTo(destinationFile);

            Image image = new Image();
            image.setFilepath(destinationFile.getAbsolutePath());

            image = imageRepository.save(image);

            // now remove from a user if they have one and add new to user
            if (user.getImage() != null && user.getImage().getFilepath() != null && !user.getImage().getFilepath().isEmpty()) {
                imageRepository.delete(user.getImage());
                // also remove the file
                File file = new File(user.getImage().getFilepath());
                if (!file.delete()) {
                    logger.warn("could not delete file at location: {}", user.getImage().getFilepath());
                }
            }

            user.setImage(image);
            peopleService.saveUser(user);

            return ResponseEntity.ok().build();
        }
        catch (IOException e) {
            logger.error("Could not write data to file: {}", imageFile.getOriginalFilename());
            return ResponseEntity.status(512).build();
        }
    }

    @GetMapping(value = "/image/{username}", produces = MediaType.IMAGE_JPEG_VALUE)
    @Operation(summary = "get profile picture", description = "gets a profile picture for a particular user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "104", description = "Image not found for user", useReturnTypeSchema = false),
            @ApiResponse(responseCode = "200", description = "Status ok, request fulfilled", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found for given username", useReturnTypeSchema = false),
            @ApiResponse(responseCode = "512", description = "Allegedly there is an image, however we couldn't read it")
    })
    public ResponseEntity<byte[]> getImageByUsername(
            @PathVariable @Parameter(name = "username", description = "the username of the profile image to get", example = "ckugel")
            String username
    ) {
        Optional<People> personResult = peopleService.getUserByUsername(username);
        if (personResult.isEmpty()) {
            return ResponseEntity.status(404).build();
        }
        Image image = personResult.get().getImage();
        if (image != null && image.getFilepath() != null && !image.getFilepath().isEmpty()) {
            try {
                File imageFile = new File(image.getFilepath());
                return ResponseEntity.ok(Files.readAllBytes(imageFile.toPath()));
            }
            catch (IOException e) {
                logger.error("Could not recover from reading path: {}.\n Error: {}", image.getFilepath(), e.getMessage());
                return ResponseEntity.status(512).build();
            }
        }
        else {
            return ResponseEntity.status(104).build();
        }
    }

    @PostMapping(value = "{key}/profile")
    @Operation(summary = "add a bio", description = "whatever String is passed through the body will be added as a bio")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "status ok, request fulfilled"),
            @ApiResponse(responseCode = "404", description = "user not found"),
    })
    public ResponseEntity<Void> updateProfile(
            @PathVariable @Parameter(name = "key", description = "the user session key") Long key,
            @RequestHeader("bio") @Parameter(description = "the bio to update, null or empty string to keep same", example = "I loves to walk!") String bio,
            @RequestHeader("username") @Parameter(description = "the updated username, null or empty string to keep the same", example = "jeb") String username,
            @RequestHeader("email") @Parameter(description = "the updated email, null or empty to keep the same", example = "userOne@email.com") String email) {
        Optional<People> peopleResult = peopleService.getUserFromKey(key);
        if (peopleResult.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        People people = peopleResult.get();

        if (bio != null && !bio.isEmpty()) {
            people = peopleService.updateBio(people, bio);
        }
        if (username != null && !username.isEmpty()) {
            people = peopleService.updateUsername(people, username);
        }
        if (email != null && !email.isEmpty()) {
            peopleService.updateEmail(people, email);
        }

        return ResponseEntity.ok().build();
    }


    @GetMapping("{key}/profile/email")
    @Operation(summary = "email of user", description = "fetches the email of a given user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "status ok, request fulfilled", content = @Content),
            @ApiResponse(responseCode = "404", description = "user not found"),
    })
    public ResponseEntity<String> getEmail(@PathVariable @Parameter(name = "key", description = "the user session key") Long key) {
        Optional<People> peopleResult = peopleService.getUserFromKey(key);
        if (peopleResult.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.of(Optional.of(asJsonString(peopleResult.get().getEmail())));
    }


    @GetMapping("{key}/profile/bio")
    @Operation(summary = "bio of user", description = "fetches the bio of a given user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "status ok, request fulfilled", content = @Content),
            @ApiResponse(responseCode = "404", description = "user not found"),
    })
    public ResponseEntity<String> getBio(@PathVariable @Parameter(name = "key", description = "the user session key") Long key) {
        Optional<People> peopleResult = peopleService.getUserFromKey(key);
        if (peopleResult.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.of(Optional.of(asJsonString(peopleResult.get().getUsername())));
    }

    @GetMapping("{key}/organization")
    @Operation(summary = "organization of user", description = "fetches the organization of a given user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "status ok, request fulfilled", content = @Content),
            @ApiResponse(responseCode = "404", description = "user not found"),
    })
    public ResponseEntity<String> getOrganization(@PathVariable @Parameter(name = "key", description = "the user session key") Long key) {
        Optional<People> peopleResult = peopleService.getUserFromKey(key);
        return peopleResult.map(people -> ResponseEntity.of(Optional.of(asJsonString(people.getOrganization())))).orElseGet(() -> ResponseEntity.badRequest().build());
    }


    @GetMapping("{username}/profile/online")
    @Operation(summary = "online status of user", description = "fetches the online status of a given user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "got the status", content = @Content),
            @ApiResponse(responseCode = "404", description = "user not found"),
    })
    public ResponseEntity<Boolean> getUserLoggedIn(
            @PathVariable @Parameter(name = "username", description = "the username of the user") String username) {
        Optional<People> peopleResult = peopleService.getUserByUsername(username);
        return peopleResult.map(people -> ResponseEntity.of(Optional.of(peopleService.isUserLoggedIn(people)))).orElseGet(() -> ResponseEntity.badRequest().build());
    }


    /**
     * Gets the current league a user is in
     * @param key the key of said user
     * @return the current league of the user
     */
    /*
    @GetMapping("/league")
    public Optional<League> getCurrentLeague(@PathVariable Long key) {
        Optional<People> peopleResult = peopleService.getUserFromKey(key);
        if (peopleResult.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(peopleResult.get().getLeague());
    }
*/

    /**
     * updates or generates the league for the current user.
     * WARNING THIS IS VERY SLOW SO USE GET WHEN YOU CAN AND AVOID FREQUENT CALLS
     * @param key the user's session key
     * @return the league the user is in or nothing if there was an issue
     */
    /*
    @PutMapping("/league")
    public Optional<League> updateAndGetLeague(@PathVariable Long key) {
        Optional<People> peopleResult = peopleService.getUserFromKey(key);
        return peopleResult.map(people -> peopleService.updateLeagueForUser(people.getUsername()));
    }
*/
}
