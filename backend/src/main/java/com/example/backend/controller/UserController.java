package com.example.backend.controller;

import com.example.backend.dto.AuctionDTO;
import com.example.backend.dto.BidDTO;
import com.example.backend.dto.UserDTO;
import com.example.backend.dto.VerifyOtpRequest;
import com.example.backend.models.*;
import com.example.backend.repo.AuctionRepository;
import com.example.backend.repo.EventRepository;
import com.example.backend.repo.OtpRepository;
import com.example.backend.repo.UserRepository;
import com.example.backend.service.AuthService;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api") // Base URL for routes
//@CrossOrigin(origins = "http://localhost:5173") // Allow only your frontend
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private AuctionRepository auctionRepository;
    @Autowired
    private OtpRepository otpRepository;
    @Autowired
    private AuthService authService;
    @Autowired
    PasswordEncoder passwordEncoder;

    @PostMapping("/register/initiate")
    public ResponseEntity<String> initiateRegistration(@RequestBody OtpDocument request) {
        String result = authService.generateAndSendOtp(request.getEmail());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/register/verifyotp")
    public ResponseEntity<?> verifyOtpAndRegister(@RequestBody VerifyOtpRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());

        String email = request.getEmail();

        ResponseEntity<?> registeredUser = authService.verifyOtpAndRegister(
                request.getEmail(),
                request.getOtp(),
                user
        );
        return  registeredUser;
        //return ResponseEntity.ok("Registration successful. User email: " + email + " has been verified.");
    }

    @PostMapping("/register/resend-otp")
    public ResponseEntity<?> resendOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");

        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest().body("Email is required");
        }

        try {
            // Check if user exists
//            if (!userRepository.existsByEmail(email)) {
//                return ResponseEntity.badRequest().body("Email not found");
//            }

            // Find and invalidate any existing active OTPs for this email
            List<OtpDocument> existingOtps = otpRepository.findByEmailAndUsedFalse(email);
            for (OtpDocument doc : existingOtps) {
                doc.setUsed(true);
                otpRepository.save(doc);
            }

            // Generate and send new OTP
            String result = authService.generateAndSendOtp(email);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to resend OTP: " + e.getMessage());
        }
    }

    // Route: GET /
    @GetMapping("/")
    public ResponseEntity<String> home() {
        return ResponseEntity.ok("Welcome to the Home Page");
    }

    // Route: POST /register
//    @PostMapping("/register")
//    public ResponseEntity<User> register(@RequestBody User user) {
//        // Check if the email already exists
//        Optional<User> existingUser = userRepository.findByEmail(user.getEmail());
//        if (existingUser.isPresent()) {
//            return ResponseEntity.badRequest().body(null); // Email already exists
//        }
//
//        // Save the user
//        User savedUser = userRepository.save(user);
//        return ResponseEntity.ok(savedUser);
//    }

    // Route: POST /login
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User user) {
        // Validate user credentials
        Optional<User> existingUser = userRepository.findByEmail(user.getEmail());
        if (existingUser.isPresent()) {
            User foundUser = existingUser.get(); // Get the User object from Optional
//            if (foundUser.getPassword().equals(passwordEncoder.encode(user.getPassword()))) {
//                return ResponseEntity.ok(foundUser);
//            }
            if (foundUser.getPassword().equals(user.getPassword())) {
                return ResponseEntity.ok(foundUser);
            }
            return ResponseEntity.badRequest().body("Invalid credentials");
        }
        return ResponseEntity.badRequest().body("User not found");
    }

    // Route: GET /user/:userId
    @GetMapping("/user/{userId}")
    public ResponseEntity<UserDTO> getUserData(@PathVariable String userId) {
        Optional<User> userOptional = userRepository.findById(userId);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            // Convert User to UserDTO
            UserDTO userDTO = new UserDTO();
            userDTO.setId(user.getId());
            userDTO.setUsername(user.getUsername());
            userDTO.setEmail(user.getEmail());
            userDTO.setInstagram(user.getInstagram());
            userDTO.setLinkedin(user.getLinkedin());
            userDTO.setTwitter(user.getTwitter());
            userDTO.setPhoneNumber(user.getPhoneNumber());
            userDTO.setCompanyName(user.getCompanyName());

            userDTO.setEvents(eventRepository.findByHost(new ObjectId(userId)));

            List<String> auctionIds = user.getParticipatedAuctions().stream()
                    .map(ObjectId::toHexString) // Convert ObjectId to String
                    .collect(Collectors.toList());

            List<Auction> auctions = auctionRepository.findAllById(auctionIds);
            List<AuctionDTO> auctionDTOs = auctions.stream()
                    .map(auction -> {
                        AuctionDTO dto = new AuctionDTO();
                        dto.setId(auction.getId());
                        Optional<Event> event=eventRepository.findById(String.valueOf(auction.getEvent()));
                        if(event.isPresent()) {
                            dto.setEvent(event.get());
                        }
                         // If Event is already a DTO or needs conversion
                        dto.setItemName(auction.getItemName());
                        dto.setItemDescription(auction.getItemDescription());
                        dto.setStartingBid(auction.getStartingBid());
                        dto.setBidIncrement(auction.getBidIncrement());
                        // Convert Instant to LocalDateTime if needed
                        dto.setAuctionStartTime(LocalDateTime.ofInstant(auction.getAuctionStartTime(), ZoneId.systemDefault()));
                        dto.setAuctionEndTime(LocalDateTime.ofInstant(auction.getAuctionEndTime(), ZoneId.systemDefault()));
                        dto.setDuration(auction.getDuration());
                        dto.setStatus(auction.getStatus());

                        // Convert Bids to BidDTOs (you'll need to implement this conversion)
                        List<BidDTO> bidDTOs = convertToBidDTOs(auction.getBids());
                        dto.setBids(bidDTOs);

                        dto.setBenefits(auction.getBenefits());
                        dto.setImages(auction.getImages());
                        dto.setCurrentHighestBid(auction.getCurrentHighestBid());
                        dto.setCreatedAt(auction.getCreatedAt());
                        dto.setUpdatedAt(auction.getUpdatedAt());

                        return dto;
                    })
                    .toList();
            userDTO.setParticipatedAuctions(auctionDTOs);

            return ResponseEntity.ok(userDTO);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // Additional CRUD Operations for User

    @PostMapping("/uploadProfileImage/{userId}")
    public ResponseEntity<?> uploadProfileImage(@PathVariable String userId, @RequestParam("file") MultipartFile file) {
        try {
            Optional<User> userOptional = userRepository.findById(userId);
            if (!userOptional.isPresent()) {
                return ResponseEntity.badRequest().body("User not found");
            }

            User user = userOptional.get();
            String fileName = userId + "_" + file.getOriginalFilename();
            Path uploadPath = Paths.get("uploads/");

            // Ensure directory exists
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Path filePath = uploadPath.resolve(fileName);
            Files.write(filePath, file.getBytes());

            // Store relative URL in database
            String imageUrl = "/uploads/" + fileName;
            user.setProfileImage(imageUrl);
            userRepository.save(user);

            return ResponseEntity.ok(imageUrl);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error uploading image");
        }
    }

    // Route: GET /profileImage/{fileName} (Serve profile image)
    @GetMapping("/uploads/{fileName}")
    public ResponseEntity<Resource> getProfileImage(@PathVariable String fileName) throws IOException {
        Path filePath = Paths.get("uploads").resolve(fileName);

        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(filePath.toUri());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG) // Change based on actual file type
                .body(resource);
    }

    // Route: GET /users
    @GetMapping("/users")
    public List<User> getAllUsers() {
        return userRepository.findAll(); // Returns all users
    }

    // Route: PUT /user/:id
    @PutMapping("/user/{id}")
    public ResponseEntity<User> updateUser(@PathVariable String id, @RequestBody User userDetails) {
        Optional<User> existingUser = userRepository.findById(id);
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            user.setUsername(userDetails.getUsername());
            user.setEmail(userDetails.getEmail());
            //user.setPassword(userDetails.getPassword());
            if (userDetails.getInstagram() != null) user.setInstagram(userDetails.getInstagram());
            if (userDetails.getTwitter() != null) user.setTwitter(userDetails.getTwitter());
            if (userDetails.getLinkedin() != null) user.setLinkedin(userDetails.getLinkedin());
            if (userDetails.getCompanyName() != null) user.setCompanyName(userDetails.getCompanyName());
            if (userDetails.getPhoneNumber() != null)   user.setPhoneNumber(userDetails.getPhoneNumber());
            return ResponseEntity.ok(userRepository.save(user));
        }
        return ResponseEntity.notFound().build();
    }

    // Route: DELETE /user/:id
    @DeleteMapping("/user/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    private List<BidDTO> convertToBidDTOs(List<Bid> bids) {
        return bids.stream()
                .map(bid -> {
                    BidDTO dto = new BidDTO();
                    dto.setId(bid.getId());
                    dto.setAmount(bid.getAmount());

                    dto.setBidder(String.valueOf(bid.getBidder()));

                    dto.setTimestamp(bid.getTimestamp());
                    return dto;
                })
                .collect(Collectors.toList());
    }

//    @GetMapping("profile/{id}")
//    public ResponseEntity<User> getUserProfile(@PathVariable String id) {
//        Optional<User> user = userRepository.findById(id);
//        return user.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
//    }
}