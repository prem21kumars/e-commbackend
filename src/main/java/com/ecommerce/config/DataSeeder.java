package com.ecommerce.config;

import com.ecommerce.modules.auth.entity.Role;
import com.ecommerce.modules.auth.entity.User;
import com.ecommerce.modules.auth.entity.UserStatus;
import com.ecommerce.modules.auth.repository.RoleRepository;
import com.ecommerce.modules.auth.repository.UserRepository;
import com.ecommerce.modules.category.entity.Category;
import com.ecommerce.modules.category.repository.CategoryRepository;
import com.ecommerce.modules.inventory.entity.Inventory;
import com.ecommerce.modules.product.entity.Product;
import com.ecommerce.modules.product.entity.ProductImage;
import com.ecommerce.modules.product.entity.ProductStatus;
import com.ecommerce.modules.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Checking database seed state...");
        seedRolesAndUsers();
        seedCategoriesAndProducts();
        log.info("Database seeding check completed.");
    }

    private void seedRolesAndUsers() {
        Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElseGet(() ->
                roleRepository.save(Role.builder().name("ROLE_ADMIN").description("Administrative access").build()));

        Role customerRole = roleRepository.findByName("ROLE_CUSTOMER").orElseGet(() ->
                roleRepository.save(Role.builder().name("ROLE_CUSTOMER").description("Standard customer").build()));

        roleRepository.findByName("ROLE_SUPER_ADMIN").orElseGet(() ->
                roleRepository.save(Role.builder().name("ROLE_SUPER_ADMIN").description("Platform super user").build()));

        // Admin User
        if (!userRepository.existsByEmail("admin@ootoys.com")) {
            User admin = User.builder()
                    .email("admin@ootoys.com")
                    .passwordHash(passwordEncoder.encode("Admin@123456"))
                    .firstName("OO Toys")
                    .lastName("Admin")
                    .phoneNumber("+919999988888")
                    .status(UserStatus.ACTIVE)
                    .emailVerified(true)
                    .roles(Set.of(adminRole, customerRole))
                    .build();
            userRepository.save(admin);
            log.info("Seeded default admin user: admin@ootoys.com");
        }

        // Customer User
        if (!userRepository.existsByEmail("priya@example.com")) {
            User customer = User.builder()
                    .email("priya@example.com")
                    .passwordHash(passwordEncoder.encode("Customer@123456"))
                    .firstName("Priya")
                    .lastName("Sharma")
                    .phoneNumber("+919876543210")
                    .status(UserStatus.ACTIVE)
                    .emailVerified(true)
                    .roles(Set.of(customerRole))
                    .build();
            userRepository.save(customer);
            log.info("Seeded default customer user: priya@example.com");
        }
    }

    private void seedCategoriesAndProducts() {
        if (categoryRepository.count() > 0 && productRepository.count() > 0) {
            log.info("Categories and products already present in database (count: {} categories, {} products). Skipping seeding.",
                    categoryRepository.count(), productRepository.count());
            return;
        }

        // 1. Categories
        Map<String, Category> catMap = new HashMap<>();

        createCategoryIfAbsent(catMap, "Educational Toys", "educational-toys",
                "STEM kits, science discovery sets, and early learning gadgets to spark curious minds.",
                "https://i.etsystatic.com/66558843/r/il/51f202/8415066900/il_794xN.8415066900_8l9h.jpg", 1);

        createCategoryIfAbsent(catMap, "Building Blocks", "building-blocks",
                "Creative brick sets, architectural towers, and magnetic tiles for limitless invention.",
                "https://images.unsplash.com/photo-1587654780291-39c9404d746b?auto=format&fit=crop&w=600&q=80", 2);

        createCategoryIfAbsent(catMap, "Toy Cars", "toy-cars",
                "High-speed RC racers, die-cast supercars, stunt trucks, and monster tracks.",
                "https://images.unsplash.com/photo-1581235720704-06d3acfcb36f?auto=format&fit=crop&w=600&q=80", 3);

        createCategoryIfAbsent(catMap, "Dolls & Playsets", "dolls",
                "Fashion dolls, dream dollhouses, superhero figurines, and magical tea party sets.",
                "https://images.unsplash.com/photo-1500990702037-7620ccb6a60a?auto=format&fit=crop&w=600&q=80", 4);

        createCategoryIfAbsent(catMap, "Soft Toys", "soft-toys",
                "Ultra-cuddly teddy bears, plush animals, bedtime pillows, and gentle squeakers.",
                "https://images.unsplash.com/photo-1559454403-b8fb88521f11?auto=format&fit=crop&w=600&q=80", 5);

        createCategoryIfAbsent(catMap, "Board Games & Puzzles", "board-games",
                "Strategic family board games, wooden jigsaw puzzles, brain teasers, and mystery cards.",
                "https://images.unsplash.com/photo-1610890716171-6b1bb98ffd09?auto=format&fit=crop&w=600&q=80", 6);

        createCategoryIfAbsent(catMap, "Art & Craft", "art-craft",
                "Washable finger paints, modeling clay, pottery sets, and DIY bracelet beads.",
                "https://images.unsplash.com/photo-1513364776144-60967b0f800f?auto=format&fit=crop&w=600&q=80", 7);

        createCategoryIfAbsent(catMap, "Outdoor & Sports", "outdoor-sports",
                "Ride-on balance bikes, football nets, water blasters, badminton rackets, and bounce toys.",
                "https://images.unsplash.com/photo-1533227268428-f9ed0900fb3b?auto=format&fit=crop&w=600&q=80", 8);

        // 2. Products
        Category edu = catMap.get("educational-toys");
        Category blocks = catMap.get("building-blocks");
        Category cars = catMap.get("toy-cars");
        Category dolls = catMap.get("dolls");
        Category soft = catMap.get("soft-toys");
        Category games = catMap.get("board-games");
        Category arts = catMap.get("art-craft");
        Category outdoor = catMap.get("outdoor-sports");

        createProduct("RoboMaster Coding & STEM Science Kit", "robomaster-coding-stem-science-kit", "TW-EDU-0001",
                "Interactive programmable robot kit with obstacle avoidance sensors, Bluetooth mobile app control, and 50+ easy coding tutorials for kids.",
                edu, new BigDecimal("3499.00"), new BigDecimal("2799.00"), true,
                "https://i.etsystatic.com/66558843/r/il/51f202/8415066900/il_794xN.8415066900_8l9h.jpg", 25, new BigDecimal("4.8"), 142);

        createProduct("Solar System Planetary Orbit Projector", "solar-system-planetary-orbit-projector", "TW-EDU-0002",
                "Motorized celestial globe that casts stars and 8 rotating planets onto ceilings with realistic space audio commentary.",
                edu, new BigDecimal("1899.00"), new BigDecimal("1614.00"), true,
                "https://images.unsplash.com/photo-1596461404969-9ae70f2830c1?auto=format&fit=crop&w=800&q=80", 18, new BigDecimal("4.7"), 89);

        createProduct("Smart Wooden Alphabet & Sound Peg Board", "smart-wooden-alphabet-sound-peg-board", "TW-EDU-0003",
                "Tactile natural wood phonics board with cheerful animal noises and letter recognition for toddlers learning their first words.",
                edu, new BigDecimal("899.00"), new BigDecimal("809.00"), false,
                "https://images.unsplash.com/photo-1596461404969-9ae70f2830c1?auto=format&fit=crop&w=600&q=80", 40, new BigDecimal("4.9"), 210);

        createProduct("Mega Mechanical Crane & Engineering Rig", "mega-mechanical-crane-engineering-rig", "TW-BLK-0004",
                "Heavy-duty 850-piece motorized construction crane with rotating boom, winch pulleys, and real working pneumatic pistons.",
                blocks, new BigDecimal("4299.00"), new BigDecimal("3224.00"), true,
                "https://images.unsplash.com/photo-1587654780291-39c9404d746b?auto=format&fit=crop&w=800&q=80", 12, new BigDecimal("4.9"), 94);

        createProduct("Magnetic 3D Marble Run Castle (120 Pcs)", "magnetic-3d-marble-run-castle", "TW-BLK-0005",
                "Translucent vibrant magnetic geometric tiles with light-up flashing neon marbles racing through chutes, loops, and funnels.",
                blocks, new BigDecimal("2499.00"), null, false,
                "https://images.unsplash.com/photo-1585366119957-e9730b6d0f60?auto=format&fit=crop&w=800&q=80", 30, new BigDecimal("4.6"), 76);

        createProduct("Apex 4WD High-Speed Offroad RC Monster Truck", "apex-4wd-high-speed-rc-monster-truck", "TW-CAR-0007",
                "All-terrain waterproof remote controlled monster buggy with oil-filled metal shocks, 45 km/h top speed, and dual rechargeable batteries.",
                cars, new BigDecimal("3799.00"), new BigDecimal("2849.00"), true,
                "https://images.unsplash.com/photo-1581235720704-06d3acfcb36f?auto=format&fit=crop&w=800&q=80", 15, new BigDecimal("4.8"), 165);

        createProduct("Deluxe Wooden 3-Story Victorian Dollhouse", "deluxe-wooden-victorian-dollhouse", "TW-DOL-0010",
                "Handcrafted eco-timber doll mansion with working chandelier lights, elevator glide, 18 miniature furniture accessories, and open veranda.",
                dolls, new BigDecimal("5499.00"), new BigDecimal("4399.00"), true,
                "https://images.unsplash.com/photo-1500990702037-7620ccb6a60a?auto=format&fit=crop&w=800&q=80", 8, new BigDecimal("4.9"), 112);

        createProduct("Barnaby The Giant Cuddle Brown Bear (100cm)", "barnaby-giant-cuddle-brown-bear", "TW-SFT-0013",
                "Plush jumbo hypoallergenic bedtime teddy stuffed with ultra-soft cloud cotton and stitched child-safe glass-bead embroidered eyes.",
                soft, new BigDecimal("1999.00"), new BigDecimal("1599.00"), false,
                "https://images.unsplash.com/photo-1559454403-b8fb88521f11?auto=format&fit=crop&w=800&q=80", 22, new BigDecimal("4.9"), 320);

        createProduct("Kingdoms of Eldoria Strategy Board Game", "kingdoms-of-eldoria-board-game", "TW-BRD-0016",
                "Immersive 2-4 player resource gathering and castle siege game featuring painted miniatures, customized dice, and tile modular map.",
                games, new BigDecimal("2199.00"), null, false,
                "https://images.unsplash.com/photo-1610890716171-6b1bb98ffd09?auto=format&fit=crop&w=800&q=80", 19, new BigDecimal("4.7"), 63);

        createProduct("Spin Art Deluxe Electric Pottery Wheel", "spin-art-electric-pottery-wheel", "TW-ART-0019",
                "Foot-pedal controlled variable speed pottery wheel with 2 kg non-toxic terracotta clay, sculpting chisels, and glossy ceramic paints.",
                arts, new BigDecimal("2699.00"), new BigDecimal("2159.00"), true,
                "https://images.unsplash.com/photo-1513364776144-60967b0f800f?auto=format&fit=crop&w=800&q=80", 14, new BigDecimal("4.7"), 54);

        createProduct("AeroGlide Kids Lightweight Balance Bike (Ages 2-5)", "aeroglide-kids-balance-bike", "TW-OUT-0022",
                "Magnesium alloy ultralight frame with puncture-proof rubber tires, padded adjustable quick-release seat, and safety steering limiter.",
                outdoor, new BigDecimal("3999.00"), new BigDecimal("3199.00"), true,
                "https://images.unsplash.com/photo-1533227268428-f9ed0900fb3b?auto=format&fit=crop&w=800&q=80", 11, new BigDecimal("4.8"), 145);

        log.info("Catalog seeding finished successfully.");
    }

    private void createCategoryIfAbsent(Map<String, Category> map, String name, String slug, String desc, String img, int order) {
        Category category = categoryRepository.findBySlug(slug).orElseGet(() ->
                categoryRepository.save(Category.builder()
                        .name(name)
                        .slug(slug)
                        .description(desc)
                        .imageUrl(img)
                        .displayOrder(order)
                        .active(true)
                        .build()));
        map.put(slug, category);
    }

    private void createProduct(String name, String slug, String sku, String desc, Category category,
                              BigDecimal basePrice, BigDecimal salePrice, boolean featured,
                              String imageUrl, int stock, BigDecimal rating, int reviews) {
        if (productRepository.existsBySku(sku)) {
            return;
        }

        Product product = Product.builder()
                .name(name)
                .slug(slug)
                .sku(sku)
                .description(desc)
                .shortDescription(desc.length() > 150 ? desc.substring(0, 147) + "..." : desc)
                .category(category)
                .basePrice(basePrice)
                .salePrice(salePrice)
                .status(ProductStatus.PUBLISHED)
                .featured(featured)
                .averageRating(rating)
                .totalReviews(reviews)
                .build();

        ProductImage image = ProductImage.builder()
                .product(product)
                .imageUrl(imageUrl)
                .altText(name)
                .primary(true)
                .displayOrder(0)
                .build();
        product.getImages().add(image);

        Inventory inventory = Inventory.builder()
                .product(product)
                .quantityAvailable(stock)
                .quantityReserved(0)
                .lowStockThreshold(5)
                .build();
        product.setInventory(inventory);

        productRepository.save(product);
        log.info("Seeded catalog product: {} ({})", name, sku);
    }
}
