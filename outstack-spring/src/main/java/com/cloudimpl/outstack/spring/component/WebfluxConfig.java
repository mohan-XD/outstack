///*
// * Copyright 2021 nuwan.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package com.cloudimpl.outstack.spring.component;
//
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.reactive.config.ResourceHandlerRegistry;
//import org.springframework.web.reactive.config.WebFluxConfigurer;
//
///**
// *
// * @author nuwan
// */
//@Configuration
//public class WebfluxConfig implements WebFluxConfigurer {
//
//    @Override
//    public void addResourceHandlers(ResourceHandlerRegistry registry) {
//
//        registry.addResourceHandler("/swagger-ui.html**")
//                .addResourceLocations("classpath:/META-INF/resources/");
//
//        registry.addResourceHandler("/webjars/**")
//                .addResourceLocations("classpath:/META-INF/resources/webjars/");
//    }
//}