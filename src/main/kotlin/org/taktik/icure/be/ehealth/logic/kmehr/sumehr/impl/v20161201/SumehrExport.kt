/*
 * Copyright (C) 2018 Taktik SA
 *
 * This file is part of iCureBackend.
 *
 * iCureBackend is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * iCureBackend is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with iCureBackend.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.taktik.icure.be.ehealth.logic.kmehr.sumehr.impl.v20161201

import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.logging.LogFactory
import org.taktik.icure.be.ehealth.dto.kmehr.v20161201.Utils
import org.taktik.icure.be.ehealth.dto.kmehr.v20161201.be.fgov.ehealth.standards.kmehr.cd.v1.*
import org.taktik.icure.be.ehealth.dto.kmehr.v20161201.be.fgov.ehealth.standards.kmehr.dt.v1.TextType
import org.taktik.icure.be.ehealth.dto.kmehr.v20161201.be.fgov.ehealth.standards.kmehr.id.v1.IDKMEHR
import org.taktik.icure.be.ehealth.dto.kmehr.v20161201.be.fgov.ehealth.standards.kmehr.id.v1.IDKMEHRschemes
import org.taktik.icure.be.ehealth.dto.kmehr.v20161201.be.fgov.ehealth.standards.kmehr.schema.v1.*
import org.taktik.icure.be.ehealth.logic.kmehr.v20161201.KmehrExport
import org.taktik.icure.constants.ServiceStatus
import org.taktik.icure.entities.HealthElement
import org.taktik.icure.entities.HealthcareParty
import org.taktik.icure.entities.Patient
import org.taktik.icure.entities.base.ICureDocument
import org.taktik.icure.entities.embed.Content
import org.taktik.icure.entities.embed.Partnership
import org.taktik.icure.entities.embed.PatientHealthCareParty
import org.taktik.icure.entities.embed.Service
import org.taktik.icure.services.external.api.AsyncDecrypt
import org.taktik.icure.services.external.rest.v1.dto.HealthElementDto
import org.taktik.icure.services.external.rest.v1.dto.embed.ServiceDto
import org.taktik.icure.services.external.rest.v1.dto.filter.Filters
import org.taktik.icure.services.external.rest.v1.dto.filter.service.ServiceByHcPartyTagCodeDateFilter
import org.taktik.icure.utils.FuzzyValues
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller

/**
 * Created with IntelliJ IDEA.
 * User: aduchate
 * Date: 17/12/13
 * Time: 22:58
 * To change this template use File | Settings | File Templates.
 */
@org.springframework.stereotype.Service("sumehrExportV2")
class SumehrExport : KmehrExport() {
	override val log = LogFactory.getLog(SumehrExport::class.java)

	fun getMd5(hcPartyId: String, patient: Patient, sfks: List<String>, excludedIds: List<String>): String {
		val signatures = ArrayList(listOf(patient.signature))
		getAllServices(hcPartyId, sfks, excludedIds).forEach { signatures.add(it.modified.toString()) }
		getHealthElements(hcPartyId, sfks, excludedIds).forEach { signatures.add(it.modified.toString()) }

		val sorted = signatures.sorted()

		val md5Hex = DigestUtils.md5Hex(sorted.joinToString(","))
		return md5Hex
	}

	fun createSumehr(
			os: OutputStream,
			pat: Patient,
			sfks: List<String>,
			sender: HealthcareParty,
			recipient: HealthcareParty?,
			language: String,
			comment: String?,
			excludedIds: List<String>,
			decryptor: AsyncDecrypt?,
			config: Config = Config(_kmehrId = System.currentTimeMillis().toString(),
					date = makeXGC(Instant.now().toEpochMilli())!!,
					time = Utils.makeXGC(Instant.now().toEpochMilli(), true)!!,
					soft = Config.Software(name = "iCure", version = ICUREVERSION),
					clinicalSummaryType = "",
					defaultLanguage = "en"
			)
	) {
		val message = initializeMessage(sender, config)
		message.header.recipients.add(RecipientType().apply {
			hcparties.add(recipient?.let { createParty(it, emptyList()) } ?: createParty(emptyList(), listOf(CDHCPARTY().apply { s = CDHCPARTYschemes.CD_APPLICATION; sv = "1.0" }), "gp-software-migration"))
		})

		val folder = FolderType()
		folder.ids.add(IDKMEHR().apply { s = IDKMEHRschemes.ID_KMEHR; sv = "1.0"; value = 1.toString() })
		folder.patient = makePerson(pat, config)
		fillPatientFolder(folder, pat, sfks, sender, language, config, comment, excludedIds, decryptor)
		message.folders.add(folder)

		val jaxbMarshaller = JAXBContext.newInstance(Kmehrmessage::class.java).createMarshaller()
		// output pretty printed
		jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
		jaxbMarshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8")
		jaxbMarshaller.marshal(message, OutputStreamWriter(os, "UTF-8"))
	}

	internal fun fillPatientFolder(folder: FolderType, p: Patient, sfks: List<String>, sender: HealthcareParty, language: String, config: Config, comment: String?, excludedIds: List<String>, decryptor: AsyncDecrypt?): FolderType {
		val trn = TransactionType().apply {
			cds.add(CDTRANSACTION().apply { s = CDTRANSACTIONschemes.CD_TRANSACTION; sv = "1.0"; value = "sumehr" })
			author = AuthorType().apply { hcparties.add(createParty(sender, emptyList())) }
			ids.add(IDKMEHR().apply { s = IDKMEHRschemes.ID_KMEHR; sv = "1.0"; value = "1" })
			ids.add(IDKMEHR().apply { s = IDKMEHRschemes.LOCAL; sl = "iCure-Item"; sv = ICUREVERSION; value = p.id.replace("-".toRegex(), "").substring(0, 8) + "." + System.currentTimeMillis() })
			makeXGC(System.currentTimeMillis()).let { date = it; time = it }
			isIscomplete = true
			isIsvalidated = true
		}

		folder.transactions.add(trn)

		addNonPassiveIrrelevantServiceUsingContent(sender.id, sfks, trn, "adr", language, excludedIds, decryptor)
		addNonPassiveIrrelevantServiceUsingContent(sender.id, sfks, trn, "allergy", language, excludedIds, decryptor)
		addNonPassiveIrrelevantServiceUsingContent(sender.id, sfks, trn, "socialrisk", language, excludedIds, decryptor)
		addNonPassiveIrrelevantServiceUsingContent(sender.id, sfks, trn, "risk", language, excludedIds, decryptor)
		//itemIndex = addNonPassiveIrrelevantServiceUsingContent(p, trn, itemIndex, "familyrisk");

		addGmdmanager(p, trn)
		addContactPeople(p, trn, config, excludedIds)
		addPatientHealthcareParties(p, trn, config, excludedIds)


		addNonPassiveIrrelevantServicesAsCD(sender.id, sfks, trn, "patientwill", CDCONTENTschemes.CD_PATIENTWILL, listOf("ntbr", "bloodtransfusionrefusal", "intubationrefusal", "euthanasiarequest", "vaccinationrefusal", "organdonationconsent", "datareuseforclinicalresearchconsent", "datareuseforclinicaltrialsconsent", "clinicaltrialparticipationconsent"), excludedIds, decryptor)

		addVaccines(sender.id, sfks, trn, excludedIds, decryptor)
		addMedications(sender.id, sfks, trn, excludedIds, decryptor)

		addNonPassiveIrrelevantServiceUsingContent(sender.id, sfks, trn, "healthissue", language, excludedIds, decryptor, false, "problem")
		addNonPassiveIrrelevantServiceUsingContent(sender.id, sfks, trn, "healthcareelement", language, excludedIds, decryptor, false, "problem")

		addHealthCareElements(sender.id, sfks, trn, excludedIds, decryptor)

		if (comment?.length ?: 0 > 0) {
			trn.headingsAndItemsAndTexts.add(TextType().apply { l = sender.languages.firstOrNull() ?: "fr"; value = comment })
		}

		//Remove empty headings
		val iterator = folder.transactions[0].headingsAndItemsAndTexts.iterator()
		while (iterator.hasNext()) {
			val h = iterator.next()
			if (h is HeadingType) {
				if (h.headingsAndItemsAndTexts.size == 0) {
					iterator.remove()
				}
			}
		}
		return folder
	}

	fun getAllServices(hcPartyId: String, sfks: List<String>, excludedIds: List<String>, decryptor: AsyncDecrypt? = null): List<Service> {
		return getNonPassiveIrrelevantServices(hcPartyId, sfks, listOf("adr", "allergy", "socialrisk", "risk", "patientwill", "healthissue", "healthcareelement"), excludedIds, decryptor) + getMedications(hcPartyId, sfks, excludedIds, decryptor) + getVaccines(hcPartyId, sfks, excludedIds, decryptor).filter { s -> !excludedIds.contains(s.id) }
	}

	fun getAllServicesPlusPlus(hcPartyId: String, sfks: List<String>, excludedIds: List<String>, decryptor: AsyncDecrypt?): List<Service> {
		return getAllServices(hcPartyId, sfks, excludedIds, decryptor)
	}

	internal fun getNonPassiveIrrelevantServices(hcPartyId: String, sfks: List<String>, cdItems: List<String>, excludedIds: List<String>, decryptor: AsyncDecrypt?): List<Service> {
		val f = Filters.UnionFilter(
				sfks.map { k ->
					Filters.UnionFilter(cdItems.map { cd ->
						ServiceByHcPartyTagCodeDateFilter(hcPartyId, k, "CD-ITEM", cd, null, null, null, null)
					}
					)
				}
		)

		var services = contactLogic?.getServices(filters?.resolve(f))?.filter { s ->
			s.endOfLife == null && //Not end of lifed
					!((ServiceStatus.isInactive(s.status) || s.tags?.any { it.type == "CD-LIFECYCLE" && it.code == "inactive" } ?: false) //Inactive
							&& ServiceStatus.isIrrelevant(s.status)) //And irrelevant
					&& (s.content.values.any { null != (it.binaryValue ?: it.booleanValue ?: it.documentId ?: it.instantValue ?: it.measureValue ?: it.medicationValue) || it.stringValue?.length ?: 0 > 0 } || s.encryptedContent?.length ?: 0 > 0 || s.encryptedSelf?.length ?: 0 > 0) //And content
		}?.filter { s -> !excludedIds.contains(s.id) }

		val toBeDecryptedServices = services?.filter { it.encryptedContent?.length ?: 0 > 0 || it.encryptedSelf?.length ?: 0 > 0 }

		if (decryptor != null && toBeDecryptedServices?.size ?: 0 > 0) {

			val decryptedServices  =  mutableListOf<Service>()

			val chunkedToBeDecryptedServices = toBeDecryptedServices?.chunked(50)

			chunkedToBeDecryptedServices?.forEach { itt ->
				val decryptedServicesChunk = decryptor.decrypt(itt?.map { mapper!!.map(it, ServiceDto::class.java) }, ServiceDto::class.java).get().map { mapper!!.map(it, Service::class.java) }
				decryptedServices.addAll(decryptedServicesChunk)
			}

			services = services?.map { if (toBeDecryptedServices?.contains(it) == true) decryptedServices[toBeDecryptedServices.indexOf(it)] else it }
		}

		return services ?: emptyList()
	}

	internal fun <T : ICureDocument> getNonConfidentialItems(items: List<T>): List<T> {
		return items.filter { s ->
			null == s.tags.find { it.type == "org.taktik.icure.entities.embed.Confidentiality" && it.code == "secret" } &&
					null == s.codes.find { it.type == "org.taktik.icure.entities.embed.Visibility" && it.code == "maskedfromsummary" }
		}
	}

	internal fun hasOmissionOfMedicalDataItem(trn: TransactionType): Boolean {
		return getAssessment(trn).headingsAndItemsAndTexts
				.filter { it != null && it is ItemType }
				.map { it as ItemType }
				.any { item ->
					item.contents.filterNotNull().any { content ->
						content.cds.filterNotNull().any { cd ->
							cd.value == CDPATIENTWILLvalues.OMISSIONOFMEDICALDATA.value()
						}
					}
				}
	}

	internal fun addOmissionOfMedicalDataItem(trn: TransactionType) {
		if (!hasOmissionOfMedicalDataItem(trn)) {
			// We automatically add (once and only once) a patient's will "omissionofmedicaldata" if some elements are confidentials.
			val assessment = getAssessment(trn)
			assessment.headingsAndItemsAndTexts.add(super.getOmissionOfMedicalDataWill(assessment.headingsAndItemsAndTexts.size + 1))
		}
	}

	internal fun <T : ICureDocument> addOmissionOfMedicalDataItem(
			trn: TransactionType,
			items: List<T>,
			nonConfidentialItems: List<T>,
			predicate: ((List<T>, List<T>) -> Boolean) = { a, b -> a.size != b.size }) {

		if (predicate(items, nonConfidentialItems))
			addOmissionOfMedicalDataItem(trn)
	}

	fun getHealthElements(hcPartyId: String, sfks: List<String>, excludedIds: List<String>): List<HealthElement> {
		return healthElementLogic?.findLatestByHCPartySecretPatientKeys(hcPartyId, sfks)?.filter {
			!(it.descr?.matches("INBOX|Etat g\\u00e9n\\u00e9ral.*".toRegex()) ?: false || (ServiceStatus.isIrrelevant(it.status) && (it.closingDate != null|| (ServiceStatus.isInactive(it.status)))))
		}?.filter { s -> !excludedIds.contains(s.id) } ?: emptyList()
	}

	fun getContactPeople(hcPartyId: String, sfks: List<String>, excludedIds: List<String>, patientId: String): List<Partnership> {
		return patientLogic?.getPatient(patientId)?.partnerships?.filter{p -> !excludedIds.contains(p.partnerId)} ?: emptyList()
	}

	fun getPatientHealthCareParties(hcPartyId: String, sfks: List<String>, excludedIds: List<String>, patientId: String): List<PatientHealthCareParty> {
		return patientLogic?.getPatient(patientId)?.patientHealthCareParties?.filter{p -> !excludedIds.contains(p.healthcarePartyId)} ?: emptyList()
	}

	internal fun getMedications(hcPartyId: String, sfks: List<String>, excludedIds: List<String>, decryptor: AsyncDecrypt?): List<Service> {
		val nowFuzzy = FuzzyValues.getCurrentFuzzyDate()
		//val medications = getNonPassiveIrrelevantServices(hcPartyId, sfks, listOf("medication"), excludedIds, decryptor).filter { it.closingDate?.let { it >= nowFuzzy } ?: true }
		val medications = getNonPassiveIrrelevantServices(hcPartyId, sfks, listOf("medication"), emptyList(), decryptor).filter { it.closingDate?.let { it >= nowFuzzy } ?: true }
		val cnks = HashSet(medications.filter { m -> m.codes.find { it.type == "CD-DRUG-CNK" } != null }.mapNotNull { m -> m.codes.find { it.type == "CD-DRUG-CNK" }?.code })
		return medications.filter{!excludedIds.contains(it.id)} + getNonPassiveIrrelevantServices(hcPartyId, sfks, listOf("treatment"), excludedIds, decryptor).filter {
			val cnk = it.codes.find { it.type == "CD-DRUG-CNK" }?.code
			val res = (null == cnk || !cnks.contains(cnk)) && ((null == it.closingDate && FuzzyValues.compare((it.openingDate ?: it.valueDate ?: 1970101), FuzzyValues.getFuzzyDate(LocalDateTime.now().minusWeeks(2), ChronoUnit.SECONDS)) > 0) || (it.closingDate?.let { it >= nowFuzzy } ?: false))
			cnk?.let { cnks.add(it) }
			res
		}
	}

	internal fun getVaccines(hcPartyId: String, sfks: List<String>, excludedIds: List<String>, decryptor: AsyncDecrypt?): List<Service> {
		return getNonPassiveIrrelevantServices(hcPartyId, sfks, listOf("vaccine"), excludedIds, decryptor).filter { it.codes.any { c -> c.type == "CD-VACCINEINDICATION" && c.code?.length ?: 0 > 0 } }
	}

	internal fun getAssessment(trn: TransactionType): HeadingType {
		var assessment = trn.headingsAndItemsAndTexts.find { h -> (h is HeadingType) && h.cds.any { cd -> cd.value == "assessment" } }
		if (assessment == null) {
			assessment = HeadingType().apply {
				ids.add(IDKMEHR().apply { s = IDKMEHRschemes.ID_KMEHR; sv = "1.0"; value = (trn.headingsAndItemsAndTexts.size + 1).toString() })
				cds.add(CDHEADING().apply { s = CDHEADINGschemes.CD_HEADING; sv = "1.0"; value = "assessment" })
			}
			trn.headingsAndItemsAndTexts.add(assessment)
		}
		return assessment as HeadingType
	}

	internal fun getHistory(trn: TransactionType): HeadingType {
		var history = trn.headingsAndItemsAndTexts.find { h -> (h is HeadingType) && h.cds.any { cd -> cd.value == "history" } }
		if (history == null) {
			history = HeadingType().apply {
				ids.add(IDKMEHR().apply { s = IDKMEHRschemes.ID_KMEHR; sv = "1.0"; value = (trn.headingsAndItemsAndTexts.size + 1).toString() })
				cds.add(CDHEADING().apply { s = CDHEADINGschemes.CD_HEADING; sv = "1.0"; value = "history" })
			}
			trn.headingsAndItemsAndTexts.add(history)
		}
		return history as HeadingType
	}

	internal fun addNonPassiveIrrelevantServicesAsCD(hcPartyId: String, sfks: List<String>, trn: TransactionType, cdItem: String, type: CDCONTENTschemes, values: List<String>, excludedIds: List<String>, decryptor: AsyncDecrypt?) {
		val assessment = getAssessment(trn)

		val services = getNonPassiveIrrelevantServices(hcPartyId, sfks, listOf(cdItem), excludedIds, decryptor)
		val nonConfidentialItems = getNonConfidentialItems(services)
		addOmissionOfMedicalDataItem(trn, services, nonConfidentialItems)

		values.forEach { value ->
			nonConfidentialItems.filter { s -> null != s.codes.find { it.type == type.value() && value == it.code } }.forEach {
				createItemWithContent(it, assessment.headingsAndItemsAndTexts.size + 1, cdItem, listOf(ContentType().apply { cds.add(CDCONTENT().apply { s = type; sv = "1.3"; this.value = value }) }))?.let {
					assessment.headingsAndItemsAndTexts.add(it)
				}
			}
		}
	}

	internal fun addNonPassiveIrrelevantServiceUsingContent(hcPartyId: String, sfks: List<String>, trn: TransactionType, cdItem: String, language: String, excludedIds: List<String>, decryptor: AsyncDecrypt?, forcePassive: Boolean = false, forceCdItem: String? = null) {
		try {
			val services = getNonPassiveIrrelevantServices(hcPartyId, sfks, listOf(cdItem), excludedIds, decryptor)
			val nonConfidentialItems = getNonConfidentialItems(services)
			addOmissionOfMedicalDataItem(trn, services, nonConfidentialItems)

			if (nonConfidentialItems.isEmpty()) {
				log.debug("_writeItems : no services found with cd-item " + cdItem)
			} else {
				nonConfidentialItems.forEach { svc ->
					val items = if (!((svc.tags.any { it.type == "CD-LIFECYCLE" && it.code == "inactive" } || ServiceStatus.isInactive(svc.status)) && !forcePassive)) {
						getAssessment(trn).headingsAndItemsAndTexts
					} else {
						getHistory(trn).headingsAndItemsAndTexts
					}

					val it = createItemWithContent(svc, items.size + 1, forceCdItem ?: cdItem, (svc.content[language]?.let { makeContent(language, it) } ?: svc.content.entries.firstOrNull()?.let { makeContent(it.key, it.value) })?.let { listOf(it) } ?: emptyList())
					if (it != null) {
                        it.contents.add(ContentType().apply {
                            svc.codes?.forEach { c ->
                                try{
                                    // CD-ATC have a version 0.0.1 in the DB. However the sumehr validator requires a CD-ATC 1.0
                                    val version = if (c.type == "CD-ATC") "1.0" else c.version
                                    // BE-THESAURUS (IBUI) are in fact CD-CLINICAL (https://www.ehealth.fgov.be/standards/kmehr/en/tables/ibui)
                                    val type = if (c.type == "BE-THESAURUS") "CD-CLINICAL" else c.type
                                    val cdt = CDCONTENTschemes.fromValue(type)
                                    this.cds.add(CDCONTENT().apply { s(cdt); sl = type; dn = type; sv = version; value = c.code })
                                } catch (ignored : IllegalArgumentException) {
                                    log.error(ignored)
                                }
                            }
                        })
						for ((key, value) in svc.content) {
							if (value.medicationValue != null) {
								fillMedicationItem(svc, it, key)
								break
							}
						}

						if (svc.comment != null) {
							it.texts.add(TextType().apply { l = "fr"; value = svc.comment })
						}
						items.add(it)
					}
				}
			}
		} catch (e: RuntimeException) {
			log.error("Unexpected error", e)
		}
	}

	internal fun createVaccineItem(svc: Service, itemIndex: Int): ItemType? {
		val item = createItemWithContent(svc, itemIndex, "vaccine", svc.content.entries.mapNotNull {
			it.value.booleanValue = null
			it.value.binaryValue = null
			it.value.documentId = null
			it.value.measureValue = null
			it.value.numberValue = null
			it.value.instantValue = null
			it.value.stringValue = null

			makeContent(it.key, it.value)
		})

		item?.let {
			addServiceCodesAndTags(svc, it, true, listOf("CD-ATC", "CD-VACCINEINDICATION"), null, listOf("CD-TRANSACTION", "CD-TRANSACTION-TYPE"))
		}
		return item
	}

	override fun createItemWithContent(svc: Service, idx: Int, cdItem: String, contents: List<ContentType>, localIdName: String): ItemType? {
		if (ServiceStatus.isAbsent(svc.status) || svc.tags.any { t -> t.type == "CD-LIFECYCLE" && t.code == "notpresent" }) {
			return null; }
		return super.createItemWithContent(svc, idx, cdItem, contents, localIdName)
	}

	override fun createItemWithContent(he: HealthElement, idx: Int, cdItem: String, contents: List<ContentType>): ItemType? {
		if (ServiceStatus.isAbsent(he.status) || he.tags.any { t -> t.type == "CD-LIFECYCLE" && t.code == "notpresent" }) {
			return null; }
		return super.createItemWithContent(he, idx, cdItem, contents)
	}

	internal fun addContactPeople(pat: Patient, trn: TransactionType, config: Config, excludedIds: List<String>) {
		patientLogic?.getPatients(pat.partnerships?.filter { s -> !excludedIds.contains(s.partnerId) }?.mapNotNull { it?.partnerId })?.forEach { p ->
			val rel = pat.partnerships.find { it.partnerId == p.id }?.otherToMeRelationshipDescription
			try {
				rel.let {
					val items = getAssessment(trn).headingsAndItemsAndTexts
					items.add(ItemType().apply {
						ids.add(IDKMEHR().apply { s = IDKMEHRschemes.ID_KMEHR; sv = "1.0"; value = (items.size + 1).toString() })
						cds.add(CDITEM().apply { s(CDITEMschemes.CD_ITEM); value = CDITEMvalues.CONTACTPERSON.value() })
						cds.add(CDITEM().apply { s(CDITEMschemes.CD_CONTACT_PERSON); value = rel })
						contents.add(ContentType().apply { person = makePerson(p, config) })
					})
				}
			} catch (e: RuntimeException) {
				log.error("Unexpected error", e)
			}
		}
	}

	internal fun addPatientHealthcareParties(pat: Patient, trn: TransactionType, config: Config, excludedIds: List<String>) {
        healthcarePartyLogic?.getHealthcareParties(pat.patientHealthCareParties?.filter { s -> !excludedIds.contains(s.healthcarePartyId) }?.mapNotNull {it?.healthcarePartyId})?.forEach { hcp ->
            if (hcp.specialityCodes?.none { c -> !c.code.startsWith("pers") } == true) {
                val phcp = pat.patientHealthCareParties.find { it.healthcarePartyId == hcp.id }
                try {
                    phcp.let {
                        val items = getAssessment(trn).headingsAndItemsAndTexts
                        items.add(ItemType().apply {
                            ids.add(IDKMEHR().apply { s = IDKMEHRschemes.ID_KMEHR; sv = "1.0"; value = (items.size + 1).toString() })
                            cds.add(CDITEM().apply { s(CDITEMschemes.CD_ITEM); value = CDITEMvalues.CONTACTHCPARTY.value() })
                            contents.add(ContentType().apply { hcparty = createParty(hcp, emptyList()) })
                        })
                    }
                } catch (e: RuntimeException) {
                    log.error("Unexpected error", e)
                }
            }
        }
    }

	internal fun addGmdmanager(pat: Patient, trn: TransactionType) {
		try {
			val gmdRelationship = pat.patientHealthCareParties?.find { it.referralPeriods?.any { r -> r.startDate.isBefore(Instant.now()) && null == r.endDate } ?: false }
			if (gmdRelationship != null) {
				healthcarePartyLogic?.getHealthcareParty(gmdRelationship.healthcarePartyId)?.let { hcp ->
					val items = getAssessment(trn).headingsAndItemsAndTexts
					items.add(ItemType().apply {
						ids.add(IDKMEHR().apply { s = IDKMEHRschemes.ID_KMEHR; sv = "1.0"; value = (items.size + 1).toString() })
						cds.add(CDITEM().apply { s(CDITEMschemes.CD_ITEM); value = "gmdmanager" })
						contents.add(ContentType().apply { hcparty = createParty(hcp, emptyList()) })
					})
				}
			}
		} catch (e: Exception) {
			log.error("Unexpected error", e)
		}
	}

	internal fun addMedications(hcPartyId: String, sfks: List<String>, trn: TransactionType, excludedIds: List<String>, decryptor: AsyncDecrypt?) {
		try {
			val medications = getMedications(hcPartyId, sfks, excludedIds, decryptor)
			val nonConfidentialItems = getNonConfidentialItems(medications)
			addOmissionOfMedicalDataItem(trn, medications, nonConfidentialItems)

			nonConfidentialItems.forEach { m ->
				if (null == m.closingDate) {
					m.closingDate = FuzzyValues.getFuzzyDate(LocalDateTime.now().plusMonths(1), ChronoUnit.SECONDS)
				}
				val items = getAssessment(trn).headingsAndItemsAndTexts
				createItemWithContent(m, items.size + 1, "medication", m.content.entries.mapNotNull {
					if ((it.value.booleanValue == true || it.value.instantValue != null || it.value.numberValue != null) && it.value.stringValue?.length ?: 0 == 0) {
						it.value.stringValue = m.label
					}
					it.value.booleanValue = null
					it.value.binaryValue = null
					it.value.documentId = null
					it.value.measureValue = null
					it.value.numberValue = null
					it.value.instantValue = null

					makeContent(it.key, it.value)
				})?.let { item ->
					if (item.contents?.size ?: 0 > 0) {
						val medicationEntry = m.content.entries.find { null != it.value.medicationValue }
						if (medicationEntry != null) {
							fillMedicationItem(m, item, medicationEntry.key)
						}
						items.add(item)
					}
				}
			}
		} catch (e: RuntimeException) {
			log.error("Unexpected error", e)
		}
	}

	internal fun addVaccines(hcPartyId: String, sfks: List<String>, trn: TransactionType, excludedIds: List<String>, decryptor: AsyncDecrypt?) {
		try {
			val vaccines = getVaccines(hcPartyId, sfks, excludedIds, decryptor)
			val nonConfidentialItems = getNonConfidentialItems(vaccines)
			addOmissionOfMedicalDataItem(trn, vaccines, nonConfidentialItems)

			nonConfidentialItems.forEach {
				val items = getAssessment(trn).headingsAndItemsAndTexts
				items.add(createVaccineItem(it, items.size + 1))
			}
		} catch (e: RuntimeException) {
			log.error("Unexpected error", e)
		}
	}

	internal fun addHealthCareElements(hcPartyId: String,
									   sfks: List<String>,
									   trn: TransactionType,
									   excludedIds: List<String>,
									   decryptor: AsyncDecrypt?) {

        val healthElements = getHealthElements(hcPartyId, sfks, excludedIds )
		var nonConfidentialItems = getNonConfidentialItems(healthElements)
		addOmissionOfMedicalDataItem(trn, healthElements, nonConfidentialItems)

		val toBeDecryptedHcElements = nonConfidentialItems.filter { it.encryptedSelf?.length ?: 0 > 0 }

		if (decryptor != null && toBeDecryptedHcElements.size ?: 0 >0) {
			val decryptedHcElements = decryptor.decrypt(toBeDecryptedHcElements.map {mapper!!.map(it, HealthElementDto::class.java)}, HealthElementDto::class.java).get().map {mapper!!.map(it, HealthElement::class.java)}
			nonConfidentialItems = nonConfidentialItems?.map { if (toBeDecryptedHcElements.contains(it) == true) decryptedHcElements[toBeDecryptedHcElements.indexOf(it)] else it }
		}

		for (healthElement in nonConfidentialItems) {
			addHealthCareElement(trn, healthElement)
		}
	}

	internal fun addHealthCareElement(trn: TransactionType, eds: HealthElement) {
		try {
			val items = if (eds.closingDate != null) {
				getHistory(trn).headingsAndItemsAndTexts
			} else {
				getAssessment(trn).headingsAndItemsAndTexts
			}

			// familyrisk not allowed in Sumehr
			eds.tags?.find {it.type == "CD-ITEM" && it.code == "familyrisk"}?.apply {
				code = "healthcareelement"
			}

			// healthcareelement not allowed anymore in sumehr V2. Use "problem" instead.
			// https://www.ehealth.fgov.be/standards/kmehr/en/transactions/summarised-electronic-healthcare-record-v11
			// https://www.ehealth.fgov.be/standards/kmehr/en/transactions/summarised-electronic-healthcare-record-v20
			eds.tags?.find {it.type == "CD-ITEM" && it.code == "healthcareelement"}?.apply {
				code = "problem"
				version = "1.11"
			}


			listOf("problem", "allergy", "adr", "risk", "socialrisk").forEach { edType ->
				if(eds.tags?.find {it.type == "CD-ITEM" && it.code == edType} != null){
                    createItemWithContent(eds, items.size+1, edType, listOf(makeContent("fr", Content(eds.descr))).filterNotNull())?.let {
                        eds.note?.trim()?.let { note -> if(note.isNotEmpty()) it.texts.add(TextType().apply { value = note; l = "fr" }) };
                        it.contents.add(ContentType().apply {
                            eds.codes?.forEach { c ->
                                try{
                                    // CD-ATC have a version 0.0.1 in the DB. However the sumehr validator requires a CD-ATC 1.0
                                    val version = if (c.type == "CD-ATC") "1.0" else c.version
                                    // BE-THESAURUS (IBUI) are in fact CD-CLINICAL (https://www.ehealth.fgov.be/standards/kmehr/en/tables/ibui)
                                    val type = if (c.type == "BE-THESAURUS") "CD-CLINICAL" else c.type
                                    val cdt = CDCONTENTschemes.fromValue(type)
                                    this.cds.add(CDCONTENT().apply { s(cdt); sl = type; dn = type; sv = version; value = c.code })
                                } catch (ignored : IllegalArgumentException) {
                                    log.error(ignored)
                                }
                            }
                        })
                        items.add(it)
                    }
				}
			}
		} catch (e: Exception) {
			log.error("Unexpected error", e)
		}
	}

	override fun addServiceCodesAndTags(svc: Service, item: ItemType, skipCdItem: Boolean, restrictedTypes: List<String>?, uniqueTypes: List<String>?, excludedTypes: List<String>?) {
		super.addServiceCodesAndTags(svc, item, skipCdItem, restrictedTypes, uniqueTypes, (excludedTypes ?: emptyList()) + listOf("LOCAL", "RELEVANCE", "SUMEHR", "SOAP", "CD-TRANSACTION", "CD-TRANSACTION-TYPE"))
	}
}
